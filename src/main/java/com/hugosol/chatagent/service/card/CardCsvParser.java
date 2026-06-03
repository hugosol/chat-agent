package com.hugosol.chatagent.service.card;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CardCsvParser {

    private static final Map<String, Integer> CARD_STATE_MAP = Map.of(
            "New", 0,
            "Learning", 1,
            "Review", 2,
            "Relearning", 3
    );

    public List<ParsedCardRow> parse(InputStream inputStream) throws IOException {
        Reader reader = new UnicodeReader(inputStream);
        List<ParsedCardRow> rows = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .build();

        int rowNumber = 0;
        for (CSVRecord record : format.parse(reader)) {
            rowNumber++;
            String front = unescapeNewlines(getOrNull(record, "front"));
            String back = unescapeNewlines(getOrNull(record, "back"));

            FsrsFields fsrs = new FsrsFields(
                    parseDoubleOrNull(record, "stability"),
                    parseDoubleOrNull(record, "difficulty"),
                    parseCardState(record, "cardState"),
                    getOrNull(record, "due"),
                    parseIntOrNull(record, "reps"),
                    parseIntOrNull(record, "lapses"),
                    getOrNull(record, "lastReview")
            );

            rows.add(new ParsedCardRow(rowNumber, front, back, fsrs));
        }

        return rows;
    }

    private String getOrNull(CSVRecord record, String header) {
        try {
            String value = record.get(header);
            return (value != null && !value.isBlank()) ? value : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Double parseDoubleOrNull(CSVRecord record, String header) {
        String value = getOrNull(record, header);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(CSVRecord record, String header) {
        String value = getOrNull(record, header);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseCardState(CSVRecord record, String header) {
        String value = getOrNull(record, header);
        if (value == null || value.isBlank()) {
            return null;
        }
        return CARD_STATE_MAP.get(value.trim());
    }

    private String unescapeNewlines(String s) {
        if (s == null) return null;
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                if (next == '\\') {
                    sb.append('\\');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public record ParsedCardRow(int rowNumber, String front, String back, FsrsFields fsrs) {
    }

    public record FsrsFields(Double stability, Double difficulty, Integer cardState,
                              String due, Integer reps, Integer lapses, String lastReview) {
    }

    private static class UnicodeReader extends Reader {
        private final InputStreamReader delegate;

        UnicodeReader(InputStream inputStream) throws IOException {
            inputStream.mark(3);
            int b0 = inputStream.read();
            int b1 = inputStream.read();
            int b2 = inputStream.read();
            if (b0 != 0xEF || b1 != 0xBB || b2 != 0xBF) {
                inputStream.reset();
            }
            this.delegate = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return delegate.read(cbuf, off, len);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
