package com.hugosol.chatagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Client for Wiktionary REST API. Fetches word etymology.
 */
@Component
public class WiktionaryClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public WiktionaryClient(@Value("${app.wiktionary.base-url:https://en.wiktionary.org}") String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
    }

    /**
     * Fetches etymology text for a word from Wiktionary.
     *
     * @param word the word to look up
     * @return etymology text, or null if not found
     * @throws RuntimeException if the API call fails
     */
    public String fetchEtymology(String word) {
        String url = baseUrl + "/api/rest_v1/page/definition/" +
                URLEncoder.encode(word, StandardCharsets.UTF_8);

        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Wiktionary API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            var en = root.get("en");
            if (en != null && en.isArray()) {
                for (var entry : en) {
                    if (entry.has("etymology")) {
                        return entry.get("etymology").asText();
                    }
                }
            }
            return null;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Wiktionary fetch failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Wiktionary fetch interrupted", e);
        }
    }
}
