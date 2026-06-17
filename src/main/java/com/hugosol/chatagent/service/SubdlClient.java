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
import java.util.ArrayList;
import java.util.List;

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

        // Step 1: search by IMDB ID
        List<int[]> candidates = searchSubtitles(imdbId);
        if (candidates.isEmpty()) {
            throw new RuntimeException("No subtitles found on Subdl for " + imdbId);
        }

        // Step 2-3: try candidates until one downloads
        RuntimeException lastError = null;
        for (int i = 0; i < candidates.size(); i++) {
            int nId = candidates.get(i)[0];
            int fileNId = candidates.get(i)[1];
            try {
                String srt = fetchSrt(nId, fileNId);
                log.info("Subdl: downloadSrt done for {} via n_id={} file_n_id={} (candidate {}/{}), {} chars",
                        imdbId, nId, fileNId, i + 1, candidates.size(), srt.length());
                return srt;
            } catch (RuntimeException e) {
                log.warn("Subdl: candidate {}/{} (n_id={}, file_n_id={}) failed: {}",
                        i + 1, candidates.size(), nId, fileNId, e.getMessage());
                lastError = e;
            }
        }

        throw new RuntimeException("All " + candidates.size() + " Subdl candidates failed for " + imdbId
                + "; last error: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    private List<int[]> searchSubtitles(String imdbId) {
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
            JsonNode results = json.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                throw new RuntimeException("No subtitles found on Subdl for " + imdbId);
            }

            List<int[]> candidates = new ArrayList<>();
            for (JsonNode item : results) {
                JsonNode nIdNode = item.get("n_id");
                JsonNode fileNIdNode = item.get("file_n_id");
                if (nIdNode != null && fileNIdNode != null) {
                    candidates.add(new int[]{nIdNode.asInt(), fileNIdNode.asInt()});
                }
            }
            log.info("Subdl: found {} candidates for {}", candidates.size(), imdbId);
            return candidates;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Subdl search failed for " + imdbId + ": " + e.getMessage(), e);
        }
    }

    private String fetchSrt(int nId, int fileNId) {
        try {
            String url = "https://dl.subdl.com/subtitle/" + nId + "/" + fileNId
                    + "?api_key=" + apiKey;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errBody = response.body();
                log.error("Subdl download failed: HTTP {} body={}", response.statusCode(),
                        errBody.length() > 200 ? errBody.substring(0, 200) : errBody);
                throw new RuntimeException("Subdl download returned HTTP " + response.statusCode()
                        + " for n_id=" + nId + " file_n_id=" + fileNId);
            }

            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Subdl SRT download failed: " + e.getMessage(), e);
        }
    }
}
