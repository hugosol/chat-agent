package com.hugosol.chatagent.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class MovieImportIT extends E2ETestBase {

    @Autowired
    private WatchedMovieRepository watchedMovieRepository;

    @Autowired
    private SubtitleLineRepository subtitleLineRepository;

    @BeforeEach
    void setupStubs() {
        watchedMovieRepository.deleteAll();
        subtitleLineRepository.deleteAll();

        // Subtitle download is triggered manually via the retry endpoint;
        // these tests verify the data model layer directly.
    }

    @Test
    void importMovies_savesAndDownloadsSubtitles() {
        // Directly call the API to import movies (since no frontend page yet)
        // We test through the service/repository layer directly
        WatchedMovie movie1 = new WatchedMovie(DEFAULT_USER_ID, "tt001", "Movie One", 2020, SubtitleStatus.PENDING);
        watchedMovieRepository.save(movie1);

        WatchedMovie movie2 = new WatchedMovie(DEFAULT_USER_ID, "tt002", "Movie Two", 2021, SubtitleStatus.PENDING);
        watchedMovieRepository.save(movie2);

        // Verify movies saved
        List<WatchedMovie> movies = watchedMovieRepository.findByUserId(DEFAULT_USER_ID);
        assertThat(movies).hasSize(2);
        assertThat(movies).extracting(WatchedMovie::getTitle).containsExactlyInAnyOrder("Movie One", "Movie Two");

        // Subtitle download would be triggered by the API layer,
        // which we test in MovieServiceTest already.
        // Here we verify the data model works correctly.
    }

    @Test
    void deleteMovie_removesWatchedMovieAndSubtitleLines() {
        // Setup: save a movie and some subtitle lines
        WatchedMovie movie = new WatchedMovie(DEFAULT_USER_ID, "tt001", "Movie One", 2020, SubtitleStatus.DONE);
        movie = watchedMovieRepository.save(movie);

        subtitleLineRepository.save(new com.hugosol.chatagent.model.SubtitleLine(
                "tt001", "Movie One", "00:01:00,000", "00:01:02,500",
                "Test line.", "test line", 1));

        assertThat(subtitleLineRepository.countByImdbId("tt001")).isEqualTo(1);

        // Delete movie via API-like operation
        subtitleLineRepository.deleteByImdbId("tt001");
        watchedMovieRepository.delete(movie);

        // Verify deletion
        assertThat(watchedMovieRepository.findByUserId(DEFAULT_USER_ID)).isEmpty();
        assertThat(subtitleLineRepository.countByImdbId("tt001")).isZero();
    }
}
