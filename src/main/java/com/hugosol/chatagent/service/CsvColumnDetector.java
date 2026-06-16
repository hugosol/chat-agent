package com.hugosol.chatagent.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects CSV column positions by matching normalized header names
 * against known aliases for four platforms (Douban, Letterboxd, Trakt, IMDb).
 */
@Component
public class CsvColumnDetector {

    private static final Map<String, String> COLUMN_ALIASES = new HashMap<>();
    static {
        // IMDB ID aliases
        COLUMN_ALIASES.put("imdbid", "imdbId");
        COLUMN_ALIASES.put("imdb_id", "imdbId");
        COLUMN_ALIASES.put("imdb id", "imdbId");
        COLUMN_ALIASES.put("const", "imdbId");
        // Title aliases
        COLUMN_ALIASES.put("title", "title");
        COLUMN_ALIASES.put("name", "title");
        COLUMN_ALIASES.put("film", "title");
        COLUMN_ALIASES.put("movie", "title");
        // Year alias
        COLUMN_ALIASES.put("year", "year");
    }

    public record Mapping(int imdbIdIndex, int titleIndex, int yearIndex) {}

    /**
     * Detects column positions from CSV header row.
     *
     * @param headers the first row of the CSV file
     * @return mapping of standard fields to their column indices
     * @throws IllegalArgumentException if any required column is missing
     */
    public Mapping detect(List<String> headers) {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV headers must not be empty");
        }

        int imdbIdIdx = -1;
        int titleIdx = -1;
        int yearIdx = -1;

        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalize(headers.get(i));
            String field = COLUMN_ALIASES.get(normalized);
            if (field == null) continue;

            switch (field) {
                case "imdbId" -> imdbIdIdx = i;
                case "title" -> titleIdx = i;
                case "year" -> yearIdx = i;
            }
        }

        Set<String> missing = new java.util.LinkedHashSet<>();
        if (imdbIdIdx == -1) missing.add("IMDB ID");
        if (titleIdx == -1) missing.add("Title");
        if (yearIdx == -1) missing.add("Year");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required columns: " + String.join(", ", missing) +
                    ". Supported aliases: IMDB ID (imdbid/imdb_id/imdb id/const), " +
                    "Title (title/name/film/movie), Year (year)");
        }

        return new Mapping(imdbIdIdx, titleIdx, yearIdx);
    }

    private static String normalize(String header) {
        // Lowercase, trim whitespace, collapse internal spaces
        return header.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
