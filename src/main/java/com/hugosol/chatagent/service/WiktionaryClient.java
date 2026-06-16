package com.hugosol.chatagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for Wiktionary via MediaWiki Parse API.
 * <p>
 * Extracts two things for vocabulary learning:
 * <ol>
 *   <li>Source word — the most recent classical-language origin</li>
 *   <li>Derived terms — modern English words built from this word</li>
 * </ol>
 */
@Component
public class WiktionaryClient {

    private static final Logger log = LoggerFactory.getLogger(WiktionaryClient.class);

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final Pattern DERIVED_LIST_PATTERN = Pattern.compile("\\{\\{col\\|[^|}]+\\|([^}]+)\\}\\}");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int maxRetries;

    public WiktionaryClient(@Value("${app.wiktionary.base-url:https://en.wiktionary.org}") String baseUrl,
                             @Value("${app.wiktionary.connect-timeout:5s}") Duration connectTimeout,
                             @Value("${app.wiktionary.proxy-host:}") String proxyHost,
                             @Value("${app.wiktionary.proxy-port:0}") int proxyPort,
                             @Value("${app.wiktionary.max-retries:2}") int maxRetries) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }
        this.httpClient = builder.build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
    }

    /**
     * Fetches word relationship info from Wiktionary.
     *
     * @param word the word to look up
     * @return formatted string like "From Old French surrendre (sur- + rendre)\n派生: surrenderer, ..."
     *         or null if nothing found
     */
    public String fetchEtymology(String word) {
        String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8);
        String apiBase = baseUrl + "/w/api.php";

        // Step 1: Get sections
        String sectionsUrl = apiBase + "?action=parse&page=" + encodedWord
                + "&prop=sections&format=json";
        JsonNode sectionsJson = fetchJson(sectionsUrl);
        JsonNode sections = sectionsJson.path("parse").path("sections");
        if (!sections.isArray()) {
            return null;
        }

        String etyIndex = null;
        String derivedIndex = null;
        for (var section : sections) {
            String line = section.path("line").asText("");
            String level = section.path("level").asText("");
            String index = section.path("index").asText();
            if (etyIndex == null && "3".equals(level) && line.toLowerCase().contains("etymology")) {
                etyIndex = index;
            }
            if (derivedIndex == null && "5".equals(level) && "Derived terms".equalsIgnoreCase(line)) {
                derivedIndex = index;
            }
        }
        if (etyIndex == null) {
            log.debug("No etymology section found for '{}'", word);
            return null;
        }

        StringBuilder result = new StringBuilder();

        // Step 2: Fetch and parse etymology for source word
        String etyUrl = apiBase + "?action=parse&page=" + encodedWord
                + "&prop=wikitext&section=" + etyIndex + "&format=json";
        JsonNode etyJson = fetchJson(etyUrl);
        String wikitext = etyJson.path("parse").path("wikitext").path("*").asText(null);
        if (wikitext != null) {
            String source = extractSource(wikitext);
            if (source != null) {
                result.append(source);
            }
        }

        // Step 3: Fetch derived terms
        if (derivedIndex != null) {
            String derivedUrl = apiBase + "?action=parse&page=" + encodedWord
                    + "&prop=wikitext&section=" + derivedIndex + "&format=json";
            JsonNode derivedJson = fetchJson(derivedUrl);
            String derivedWikitext = derivedJson.path("parse").path("wikitext").path("*").asText(null);
            if (derivedWikitext != null) {
                String derived = extractDerivedTerms(derivedWikitext);
                if (derived != null) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append("派生: ").append(derived);
                }
            }
        }

        String output = result.toString().trim();
        return output.isEmpty() ? null : output;
    }

    // ── JSON fetch with retry ────────────────────────────────────

    private JsonNode fetchJson(String url) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "ChatAgent/1.0 (https://github.com/hugosol/chat-agent)")
                        .GET()
                        .build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429 || response.statusCode() == 403) {
                    if (attempt < maxRetries) {
                        long delay = 1000L * (attempt + 1);
                        log.debug("Wiktionary rate limited, retrying in {}ms (attempt {}/{})",
                                delay, attempt + 1, maxRetries);
                        Thread.sleep(delay);
                        continue;
                    }
                    throw new RuntimeException("Wiktionary rate limited after " + maxRetries + " retries");
                }
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Wiktionary API returned HTTP " + response.statusCode());
                }
                return objectMapper.readTree(response.body());
            } catch (java.io.IOException e) {
                if (attempt < maxRetries) {
                    log.debug("Wiktionary IO error, retrying (attempt {}/{})", attempt + 1, maxRetries, e);
                    try { Thread.sleep(500L * (attempt + 1)); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    continue;
                }
                throw new RuntimeException("Wiktionary fetch failed: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wiktionary fetch interrupted", e);
            }
        }
        throw new RuntimeException("Wiktionary fetch failed after retries");
    }

    // ── Source extraction ───────────────────────────────────────

    /**
     * Extract the most useful source word from an etymology paragraph.
     * Skips Proto-* and Middle English in favour of classical-language sources.
     */
    String extractSource(String wikitext) {
        // Find the "From ..." paragraph (skip metadata-template-only lines)
        StringBuilder paragraph = new StringBuilder();
        for (String line : wikitext.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("====")) break; // Next section
            // Skip heading and pure-template lines
            if (line.matches("=+\\s*[^=]+?\\s*=+")) continue;
            String withoutTemplates = TEMPLATE_PATTERN.matcher(line).replaceAll("");
            if (withoutTemplates.isBlank()) continue;
            paragraph.append(cleanInlineTemplates(line)).append(' ');
        }

        String text = paragraph.toString().trim();
        if (text.isEmpty()) return null;

        // Truncate at first period to get just the first sentence
        int dot = text.indexOf('.');
        if (dot > 0) {
            text = text.substring(0, dot + 1);
        }

        // Try to extract a concise source: find the best origin template
        String bestSource = extractBestSource(wikitext);
        if (bestSource != null) {
            return bestSource;
        }

        // Fallback: return first sentence as-is (max 200 chars)
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /**
     * Scans the etymology wikitext for origin templates and picks the most
     * informative non-Proto source.
     */
    private String extractBestSource(String wikitext) {
        // Collect all {{inh|en|LANG|WORD|...}} and {{bor|en|LANG|WORD|...}} and {{der|en|LANG|WORD|...}}
        List<String[]> origins = new ArrayList<>(); // [langCode, word, meaning]
        Matcher m = TEMPLATE_PATTERN.matcher(wikitext);
        while (m.find()) {
            String[] parts = m.group(1).split("\\|");
            if (parts.length < 3) continue;
            String type = parts[0].trim();
            if (!type.equals("inh") && !type.equals("bor") && !type.equals("der")) continue;
            if (!"en".equals(parts[1].trim())) continue; // Only English etymology

            String langCode = parts[2].trim();
            String word = null;
            String meaning = null;
            for (int i = 3; i < parts.length; i++) {
                String p = parts[i].trim();
                if (p.startsWith("t=")) {
                    meaning = p.substring(2);
                } else if (!p.contains("=") && !p.startsWith("-")) {
                    word = p;
                }
            }
            if (word != null && !isProtoLanguage(langCode)) {
                origins.add(new String[]{langCode, word, meaning});
            }
        }

        if (origins.isEmpty()) return null;

        // Pick the last origin (furthest back) that's not a proto-language
        String[] best = origins.get(origins.size() - 1);
        String langName = langCodeToName(best[0]);
        StringBuilder sb = new StringBuilder("From ");
        sb.append(langName).append(" ").append(best[1]);
        if (best[2] != null) {
            sb.append(" (").append(best[2]).append(")");
        }

        // Check for construction (sur- + rendre pattern)
        String construction = extractConstruction(wikitext);
        if (construction != null) {
            sb.append(" ← ").append(construction);
        }

        return sb.toString();
    }

    private boolean isProtoLanguage(String code) {
        return code.startsWith("ine-") || code.contains("pro") || code.equals("gem") || code.equals("cel");
    }

    private String langCodeToName(String code) {
        return switch (code) {
            case "ang" -> "Old English";
            case "enm" -> "Middle English";
            case "fro" -> "Old French";
            case "fr" -> "French";
            case "la" -> "Latin";
            case "grc" -> "Ancient Greek";
            case "non" -> "Old Norse";
            case "de" -> "German";
            case "nl" -> "Dutch";
            case "es" -> "Spanish";
            case "it" -> "Italian";
            case "ar" -> "Arabic";
            case "he" -> "Hebrew";
            case "sa" -> "Sanskrit";
            default -> code;
        };
    }

    /**
     * If the etymology contains a "part + part" construction like sur- + rendre,
     * extract and format it.
     */
    private String extractConstruction(String wikitext) {
        // Look for pattern: {{m|...|part1}} + {{m|...|part2}}
        Pattern plusPattern = Pattern.compile(
                "\\{\\{m\\|[^|}]+\\|([^|}]+)\\}\\}\\s*\\+\\s*\\{\\{m\\|[^|}]+\\|([^|}]+)\\}\\}");
        Matcher m = plusPattern.matcher(wikitext);
        if (m.find()) {
            return m.group(1) + " + " + m.group(2);
        }
        return null;
    }

    // ── Derived terms extraction ─────────────────────────────────

    /**
     * Extract derived terms from a "Derived terms" section wikitext.
     */
    String extractDerivedTerms(String wikitext) {
        // Look for {{col|en|word1|word2|...}} containing the word list
        Matcher m = DERIVED_LIST_PATTERN.matcher(wikitext);
        if (!m.find()) return null;

        String[] entries = m.group(1).split("\\|");
        List<String> words = new ArrayList<>();
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            // Skip sub-templates like {{l|en|word}} — just keep the text
            entry = TEMPLATE_PATTERN.matcher(entry).replaceAll("");
            entry = entry.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (!entry.isEmpty() && !entry.matches(".*[=/{}].*")) {
                words.add(entry);
            }
        }

        if (words.isEmpty()) return null;

        // Limit to 15 terms
        if (words.size() > 15) {
            words = words.subList(0, 15);
            words.add("...");
        }
        return String.join(", ", words);
    }

    // ── Inline template cleaning ─────────────────────────────────

    /**
     * Clean inline templates in a text line, keeping display words.
     * Used for the etymology paragraph fallback.
     */
    private String cleanInlineTemplates(String line) {
        String cleaned = TEMPLATE_PATTERN.matcher(line).replaceAll(mr -> {
            String[] parts = mr.group(1).split("\\|");
            String word = null;
            String meaning = null;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("t=")) {
                    meaning = part.substring(2);
                } else if (!part.contains("=") && !part.startsWith("-")) {
                    word = part;
                }
            }
            if (meaning != null && word != null) {
                return word + " (" + meaning + ")";
            }
            return word != null ? word : "";
        });
        // Remove wiki links
        cleaned = cleaned.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+?)\\]\\]", "$1");
        return cleaned.replaceAll("\\s{2,}", " ").trim();
    }
}
