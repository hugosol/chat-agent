package com.hugosol.chatagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Client for Subdl.com subtitle API.
 * <p>
 * Simple flow: search by IMDB ID → download SRT directly via constructed URL.
 * No login/JWT needed — just an API key as query parameter.
 */
@Component
public class SubdlClient {

    private static final Logger log = LoggerFactory.getLogger(SubdlClient.class);

    private final HttpClient httpClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public SubdlClient(
            @Value("${app.subdl.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Downloads the SRT subtitle file for a given IMDB ID.
     */
    public String downloadSrt(String imdbId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Subdl API key not configured (app.subdl.api-key)");
        }

        log.info("Subdl: downloadSrt start for {}", imdbId);

        List<String> urls = searchSubtitles(imdbId);
        if (urls.isEmpty()) {
            throw new RuntimeException("No subtitles found on Subdl for " + imdbId);
        }

        RuntimeException lastError = null;
        for (int i = 0; i < urls.size(); i++) {
            try {
                String srt = fetchSrt(urls.get(i));
                log.info("Subdl: downloadSrt done for {} (candidate {}/{}), {} chars",
                        imdbId, i + 1, urls.size(), srt.length());
                return srt;
            } catch (RuntimeException e) {
                log.warn("Subdl: candidate {}/{} failed: {}", i + 1, urls.size(), e.getMessage());
                lastError = e;
            }
        }

        throw new RuntimeException("All " + urls.size() + " Subdl candidates failed for " + imdbId
                + "; last error: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    private List<String> searchSubtitles(String imdbId) {
        try {
            String url = "https://api.subdl.com/api/v1/subtitles"
                    + "?api_key=" + apiKey
                    + "&imdb_id=" + imdbId
                    + "&type=movie"
                    + "&languages=en";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Subdl search returned HTTP " + response.statusCode() + " for " + imdbId);
            }

            String rawBody = response.body();
            log.info("Subdl search response for {}: {}", imdbId,
                    rawBody.length() > 500 ? rawBody.substring(0, 500) + "..." : rawBody);

            JsonNode json = objectMapper.readTree(rawBody);
            JsonNode subtitles = json.get("subtitles");
            if (subtitles == null || !subtitles.isArray() || subtitles.isEmpty()) {
                throw new RuntimeException("No subtitles found on Subdl for " + imdbId);
            }

            List<String> urls = new ArrayList<>();
            for (JsonNode item : subtitles) {
                JsonNode urlNode = item.get("url");
                if (urlNode != null && !urlNode.asText().isBlank()) {
                    urls.add(urlNode.asText());
                }
            }
            log.info("Subdl: found {} candidates for {}", urls.size(), imdbId);
            return urls;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Subdl search failed for " + imdbId + ": " + e.getMessage(), e);
        }
    }

    private String fetchSrt(String relativeUrl) {
        try {
            String fullUrl = "https://dl.subdl.com" + relativeUrl;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                String errBody = new String(response.body(), StandardCharsets.UTF_8);
                log.error("Subdl download failed: HTTP {} body={}", response.statusCode(),
                        errBody.length() > 200 ? errBody.substring(0, 200) : errBody);
                throw new RuntimeException("Subdl download returned HTTP " + response.statusCode()
                        + " for " + relativeUrl);
            }

            byte[] body = response.body();
            // Subdl returns .zip files — extract the first .srt entry
            try (var zis = new ZipInputStream(new ByteArrayInputStream(body))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".srt")) {
                        var baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            baos.write(buf, 0, len);
                        }
                        String srt = baos.toString(StandardCharsets.UTF_8);
                        log.info("Subdl: extracted {} from zip ({} chars)", entry.getName(), srt.length());
                        return srt;
                    }
                }
            }
            // If no .srt found, the body might be raw SRT (not zipped)
            String rawText = new String(body, StandardCharsets.UTF_8);
            log.info("Subdl: no .srt in zip, returning raw {} bytes", body.length);
            return rawText;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Subdl SRT download failed: " + e.getMessage(), e);
        }
    }
}
