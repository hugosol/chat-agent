package com.hugosol.chatagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Client for OpenSubtitles.com REST API.
 * <p>
 * Auth: Api-Key header identifies the consumer; username/password login returns a JWT.
 * Flow: login → search by IMDB ID → get download link → fetch SRT.
 * JWT tokens expire; this client caches the token and refreshes on 401.
 */
@Component
public class OpenSubtitlesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenSubtitlesClient.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    public OpenSubtitlesClient(
            @Value("${app.opensubtitles.api-key:}") String apiKey,
            @Value("${app.opensubtitles.username:}") String username,
            @Value("${app.opensubtitles.password:}") String password,
            @Value("${app.opensubtitles.base-url:https://api.opensubtitles.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Downloads the SRT subtitle file for a given IMDB ID.
     *
     * @param imdbId the IMDB ID (e.g. "tt1375666")
     * @return raw SRT content as string
     * @throws RuntimeException if the download fails
     */
    public String downloadSrt(String imdbId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenSubtitles API key not configured (app.opensubtitles.api-key)");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("OpenSubtitles username/password not configured (app.opensubtitles.username, app.opensubtitles.password)");
        }

        log.info("OpenSubtitles: downloadSrt start for {}", imdbId);
        String token = getToken();

        // Step 1: search for subtitle by IMDB ID — take first result
        int fileId = searchSubtitles(imdbId, token);

        // Step 2: get download link
        String downloadLink = getDownloadLink(String.valueOf(fileId), token);

        // Step 3: download SRT content
        String srt = fetchSrt(downloadLink);
        log.info("OpenSubtitles: downloadSrt done for {}, {} chars", imdbId, srt.length());
        return srt;
    }

    // ── token management ──

    private synchronized String getToken() {
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        return login();
    }

    private String login() {
        try {
            String body = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("username", username)
                            .put("password", password));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/login"))
                    .header("Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errBody = response.body();
                log.error("OpenSubtitles login failed: HTTP {} body={}", response.statusCode(), errBody);
                throw new RuntimeException("OpenSubtitles login failed: HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            cachedToken = json.get("token").asText();
            // Tokens are typically valid for 24h; cache for 23h to be safe
            tokenExpiry = Instant.now().plus(Duration.ofHours(23));
            log.info("OpenSubtitles: logged in, token valid until {}", tokenExpiry);
            return cachedToken;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenSubtitles login failed: " + e.getMessage(), e);
        }
    }

    // ── API calls ──

    private int searchSubtitles(String imdbId, String token) {
        try {
            String url = baseUrl + "/api/v1/subtitles?imdb_id=" + imdbId + "&languages=en";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Api-Key", apiKey)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                log.info("OpenSubtitles: token expired, re-logging in");
                token = login();
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Api-Key", apiKey)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenSubtitles search returned HTTP " + response.statusCode() + " for " + imdbId);
            }

            String rawBody = response.body();
            log.info("OpenSubtitles search response for {}: {}", imdbId, rawBody);
            JsonNode json = objectMapper.readTree(rawBody);
            JsonNode data = json.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new RuntimeException("No subtitles found for " + imdbId);
            }

            JsonNode files = data.get(0).get("attributes").get("files");
            if (files == null || !files.isArray() || files.isEmpty()) {
                throw new RuntimeException("Search response missing files array for " + imdbId);
            }
            int fileId = files.get(0).get("file_id").asInt();
            log.info("OpenSubtitles: found file_id={} for {}", fileId, imdbId);
            return fileId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenSubtitles search failed for " + imdbId + ": " + e.getMessage(), e);
        }
    }

    private String getDownloadLink(String fileId, String token) {
        try {
            int fid = Integer.parseInt(fileId);
            String body = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode().put("file_id", fid));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/download"))
                    .header("Api-Key", apiKey)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errBody = response.body();
                log.error("OpenSubtitles download failed: HTTP {} body={}", response.statusCode(), errBody);
                throw new RuntimeException("OpenSubtitles download returned HTTP " + response.statusCode() + " for file " + fileId);
            }

            String rawBody = response.body();
            log.info("OpenSubtitles download response for file_id={}: {}", fileId, rawBody);
            JsonNode json = objectMapper.readTree(rawBody);
            JsonNode linkNode = json.get("link");
            if (linkNode == null) {
                throw new RuntimeException("Download response missing link for file " + fileId + "; body=" + rawBody);
            }
            String link = linkNode.asText();
            log.info("OpenSubtitles: got download link for file_id={}", fileId);
            return link;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenSubtitles download failed for file " + fileId + ": " + e.getMessage(), e);
        }
    }

    private String fetchSrt(String downloadLink) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadLink))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenSubtitles SRT download returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("OpenSubtitles SRT download failed: " + e.getMessage(), e);
        }
    }
}
