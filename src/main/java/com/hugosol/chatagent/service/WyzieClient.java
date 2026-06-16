package com.hugosol.chatagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Client for Wyzie Subs API. Fetches subtitle download URLs and downloads SRT content by IMDB ID.
 */
@Component
public class WyzieClient {

    private final java.net.http.HttpClient httpClient;
    private final String baseUrl;

    public WyzieClient(@Value("${app.wyzie.base-url:https://sub.wyzie.io}") String baseUrl) {
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .build();
        this.baseUrl = baseUrl;
    }

    /**
     * Downloads the SRT subtitle file for a given IMDB ID.
     *
     * @param imdbId the IMDB ID (e.g. "tt1375666")
     * @return raw SRT content as string
     * @throws RuntimeException if the download fails
     */
    public String downloadSrt(String imdbId) {
        try {
            String url = baseUrl + "/subtitles/" + imdbId;
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();
            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Wyzie Subs API returned HTTP " + response.statusCode() + " for " + imdbId);
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to download subtitles for " + imdbId + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Subtitle download interrupted for " + imdbId, e);
        }
    }
}
