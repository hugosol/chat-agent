package com.hugosol.chatagent.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvColumnDetectorTest {

    private final CsvColumnDetector detector = new CsvColumnDetector();

    @Test
    void detectsDoubanFormat() {
        CsvColumnDetector.Mapping result = detector.detect(Arrays.asList("title", "imdbid", "year"));

        assertThat(result.imdbIdIndex()).isEqualTo(1);
        assertThat(result.titleIndex()).isEqualTo(0);
        assertThat(result.yearIndex()).isEqualTo(2);
    }

    @Test
    void detectsLetterboxdFormat() {
        CsvColumnDetector.Mapping result = detector.detect(Arrays.asList("Name", "Year", "imdb_id"));

        assertThat(result.imdbIdIndex()).isEqualTo(2);
        assertThat(result.titleIndex()).isEqualTo(0);
        assertThat(result.yearIndex()).isEqualTo(1);
    }

    @Test
    void detectsTraktFormat() {
        CsvColumnDetector.Mapping result = detector.detect(Arrays.asList("movie", "year", "imdb id"));

        assertThat(result.imdbIdIndex()).isEqualTo(2);
        assertThat(result.titleIndex()).isEqualTo(0);
        assertThat(result.yearIndex()).isEqualTo(1);
    }

    @Test
    void detectsImdbFormat() {
        CsvColumnDetector.Mapping result = detector.detect(Arrays.asList("const", "title", "year"));

        assertThat(result.imdbIdIndex()).isEqualTo(0);
        assertThat(result.titleIndex()).isEqualTo(1);
        assertThat(result.yearIndex()).isEqualTo(2);
    }

    @Test
    void normalizesWhitespaceAndCase() {
        CsvColumnDetector.Mapping result = detector.detect(Arrays.asList("  IMDB ID  ", "TITLE", "Year"));

        assertThat(result.imdbIdIndex()).isEqualTo(0);
        assertThat(result.titleIndex()).isEqualTo(1);
        assertThat(result.yearIndex()).isEqualTo(2);
    }

    @Test
    void ignoresExtraColumns() {
        CsvColumnDetector.Mapping result = detector.detect(List.of("rating", "title", "year", "imdbid", "watched_date"));

        assertThat(result.imdbIdIndex()).isEqualTo(3);
        assertThat(result.titleIndex()).isEqualTo(1);
        assertThat(result.yearIndex()).isEqualTo(2);
    }

    @Test
    void throwsWhenImdbIdColumnMissing() {
        assertThatThrownBy(() -> detector.detect(Arrays.asList("title", "year")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMDB ID");
    }

    @Test
    void throwsWhenTitleColumnMissing() {
        assertThatThrownBy(() -> detector.detect(Arrays.asList("imdbid", "year")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title");
    }

    @Test
    void throwsWhenYearColumnMissing() {
        assertThatThrownBy(() -> detector.detect(Arrays.asList("imdbid", "title")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Year");
    }

    @Test
    void handlesEmptyHeaders() {
        assertThatThrownBy(() -> detector.detect(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
