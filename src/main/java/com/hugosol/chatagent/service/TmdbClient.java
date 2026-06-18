package com.hugosol.chatagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Client for TMDB API v3. Used to search for movies by name.
 */
@Component
public class TmdbClient {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TmdbClient(@Value("${app.tmdb.api-key:}") String apiKey,
                      @Value("${app.tmdb.base-url:https://api.themoviedb.org}") String baseUrl,
                      @Value("${app.tmdb.connect-timeout:10s}") Duration connectTimeout,
                      @Value("${app.proxy.enabled:false}") boolean proxyEnabled,
                      @Value("${app.proxy.host:}") String proxyHost,
                      @Value("${app.proxy.port:0}") int proxyPort) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        var builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (proxyEnabled && proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }
        this.httpClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    public record MovieCandidate(String imdbId, String title, Integer year) {}

    /**
     * Searches TMDB for movies matching the query.
     *
     * @param query search query (supports Chinese and English)
     * @return list of candidate movies
     */
    public List<MovieCandidate> search(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TMDB API key not configured (app.tmdb.api-key)");
        }

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = baseUrl + "/3/search/movie?api_key=" + apiKey
                    + "&query=" + encoded + "&language=zh-CN";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("TMDB API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            List<MovieCandidate> candidates = new ArrayList<>();

            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    String title = node.has("title") ? node.get("title").asText() : null;
                    Integer year = null;
                    if (node.has("release_date") && !node.get("release_date").isNull()) {
                        String date = node.get("release_date").asText();
                        if (date.length() >= 4) {
                            year = Integer.parseInt(date.substring(0, 4));
                        }
                    }
                    // Fetch IMDB ID via movie details or external_ids - simplified: use TMDB ID as imdbId
                    // The caller should resolve TMDB ID → IMDB ID if needed.
                    // For now, we store TMDB ID in imdbId field, or fetch external_ids.
                    int tmdbId = node.get("id").asInt();
                    // Fetch external IDs to get IMDB ID
                    String imdbId = fetchImdbId(tmdbId);

                    if (title != null && imdbId != null) {
                        candidates.add(new MovieCandidate(imdbId, title, year));
                    }
                    if (candidates.size() >= 10) break;
                }
            }

            return candidates;
        } catch (Exception e) {
            throw new RuntimeException("TMDB search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the IMDB ID for a given TMDB movie ID via the external_ids endpoint.
     */
    public String fetchImdbId(int tmdbId) {
        try {
            String url = baseUrl + "/3/movie/" + tmdbId
                    + "/external_ids?api_key=" + apiKey;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("imdb_id") && !root.get("imdb_id").isNull()) {
                    return root.get("imdb_id").asText();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
