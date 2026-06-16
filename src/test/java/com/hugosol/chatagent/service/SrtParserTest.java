package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SrtParserTest {

    private final SrtParser parser = new SrtParser();

    @Test
    void parsesBasicSrt() {
        String srt = """
                1
                00:01:00,000 --> 00:01:02,500
                Hello world.

                2
                00:01:05,000 --> 00:01:08,000
                How are you?

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getLineIndex()).isEqualTo(1);
        assertThat(lines.get(0).getStartTime()).isEqualTo("00:01:00,000");
        assertThat(lines.get(0).getEndTime()).isEqualTo("00:01:02,500");
        assertThat(lines.get(0).getText()).isEqualTo("Hello world.");
        assertThat(lines.get(0).getWordsLower()).isEqualTo("hello world");
        assertThat(lines.get(0).getImdbId()).isEqualTo("tt001");
        assertThat(lines.get(0).getMovieTitle()).isEqualTo("Test Movie");

        assertThat(lines.get(1).getLineIndex()).isEqualTo(2);
        assertThat(lines.get(1).getText()).isEqualTo("How are you?");
    }

    @Test
    void stripsHtmlTags() {
        String srt = """
                1
                00:01:00,000 --> 00:01:02,500
                This is <i>very</i> important.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines.get(0).getText()).isEqualTo("This is very important.");
        assertThat(lines.get(0).getWordsLower()).isEqualTo("this is very important");
    }

    @Test
    void stripsComplexHtmlTags() {
        String srt = """
                1
                00:01:00,000 --> 00:01:02,500
                <font color="#ffff00">Warning:</font> Stay back.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines.get(0).getText()).isEqualTo("Warning: Stay back.");
    }

    @Test
    void joinsMultilineText() {
        String srt = """
                1
                00:01:00,000 --> 00:01:03,000
                First line.
                Second line.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines.get(0).getText()).isEqualTo("First line. Second line.");
    }

    @Test
    void generatesWordsLowerWithoutPunctuation() {
        String srt = """
                1
                00:01:00,000 --> 00:01:02,500
                You mustn't be afraid to dream a little bigger, darling!

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        // Punctuation stripped, lowercase
        assertThat(lines.get(0).getWordsLower())
                .isEqualTo("you mustn't be afraid to dream a little bigger darling");
    }

    @Test
    void handlesEmptySrt() {
        List<SubtitleLine> lines = parser.parse("", "tt001", "Test Movie");
        assertThat(lines).isEmpty();
    }

    @Test
    void handlesSrtWithSingleEntry() {
        String srt = """
                1
                00:00:05,000 --> 00:00:07,000
                Solo line.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineIndex()).isEqualTo(1);
    }

    @Test
    void assignsSequentialLineIndices() {
        String srt = """
                5
                00:01:00,000 --> 00:01:02,000
                Line A.

                10
                00:02:00,000 --> 00:02:02,000
                Line B.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        // lineIndex is sequential (1-based), not the SRT entry number
        assertThat(lines.get(0).getLineIndex()).isEqualTo(1);
        assertThat(lines.get(1).getLineIndex()).isEqualTo(2);
    }

    @Test
    void ignoresMalformedEntries() {
        String srt = """
                1
                00:01:00,000 --> 00:01:02,000
                Valid line.

                garbage
                not a valid entry

                2
                00:02:00,000 --> 00:02:02,000
                Another valid line.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        // Only properly formatted entries are parsed
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getText()).isEqualTo("Valid line.");
        assertThat(lines.get(1).getText()).isEqualTo("Another valid line.");
    }

    @Test
    void handlesMultipleTextLines() {
        String srt = """
                1
                00:01:00,000 --> 00:01:05,000
                Line one.
                Line two.
                Line three.

                """;

        List<SubtitleLine> lines = parser.parse(srt, "tt001", "Test Movie");

        assertThat(lines.get(0).getText()).isEqualTo("Line one. Line two. Line three.");
    }
}
