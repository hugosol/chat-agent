package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses SRT subtitle format into SubtitleLine objects.
 */
@Component
public class SrtParser {

    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("^(\\d{2}:\\d{2}:\\d{2},\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2},\\d{3})$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Parses raw SRT content into SubtitleLine objects.
     *
     * @param srtContent raw SRT file content
     * @param imdbId     IMDB ID for all lines
     * @param movieTitle movie title for all lines
     * @return list of parsed subtitle lines with sequential line indices
     */
    public List<SubtitleLine> parse(String srtContent, String imdbId, String movieTitle) {
        List<SubtitleLine> results = new ArrayList<>();
        if (srtContent == null || srtContent.isBlank()) {
            return results;
        }

        // Normalize line endings and split into blocks
        String normalized = srtContent.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalized.split("\n\n+");

        int lineIdx = 0;

        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            String[] lines = trimmed.split("\n");
            if (lines.length < 3) continue;

            // First line should be an index number (we ignore it, use sequential)
            // Second line should be the timestamp
            var matcher = TIMESTAMP_PATTERN.matcher(lines[1].trim());
            if (!matcher.matches()) continue;

            String startTime = matcher.group(1);
            String endTime = matcher.group(2);

            // Remaining lines are the subtitle text
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 2; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (!textBuilder.isEmpty()) textBuilder.append(" ");
                textBuilder.append(line);
            }

            String text = textBuilder.toString();
            // Strip HTML tags
            text = HTML_TAG_PATTERN.matcher(text).replaceAll("").trim();

            // Generate wordsLower: lowercase, strip punctuation, surround with spaces for boundary match
            String wordsLower = " " + text.toLowerCase()
                    .replaceAll("[^a-z0-9\\s']", "")
                    .replaceAll("\\s+", " ")
                    .trim() + " ";

            lineIdx++;
            results.add(new SubtitleLine(imdbId, movieTitle, startTime, endTime, text, wordsLower, lineIdx));
        }

        return results;
    }
}
