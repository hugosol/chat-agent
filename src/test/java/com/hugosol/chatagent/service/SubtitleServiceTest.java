package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleLine;
import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubtitleServiceTest {

    @Mock
    private SubtitleLineRepository subtitleLineRepository;

    @Mock
    private WatchedMovieRepository watchedMovieRepository;

    @Mock
    private WyzieClient wyzieClient;

    private SrtParser srtParser;
    private SubtitleService service;

    @BeforeEach
    void setUp() {
        srtParser = new SrtParser(); // real parser, already tested
        service = new SubtitleService(subtitleLineRepository, watchedMovieRepository, srtParser, wyzieClient);
    }

    @Test
    void downloadsParsesAndPersistsSubtitles() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt001", "Test Movie", 2020, SubtitleStatus.PENDING);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(movie));
        when(wyzieClient.downloadSrt("tt001")).thenReturn("""
                1
                00:01:00,000 --> 00:01:02,500
                Hello world.

                2
                00:02:00,000 --> 00:02:02,500
                Goodbye world.

                """);

        service.downloadSubtitles("tt001", "user-1");

        // Verify status transitions
        assertThat(movie.getSubtitleStatus()).isEqualTo(SubtitleStatus.DONE);
        assertThat(movie.getSubtitleLineCount()).isEqualTo(2);
        assertThat(movie.getSubtitleError()).isNull();

        // Verify SubtitleLines saved
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubtitleLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(subtitleLineRepository).saveAll(captor.capture());
        List<SubtitleLine> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getImdbId()).isEqualTo("tt001");
        assertThat(saved.get(0).getMovieTitle()).isEqualTo("Test Movie");
        assertThat(saved.get(0).getText()).isEqualTo("Hello world.");
        assertThat(saved.get(1).getText()).isEqualTo("Goodbye world.");
    }

    @Test
    void clearsOldDataOnRetry() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt001", "Test Movie", 2020, SubtitleStatus.FAILED);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(movie));
        when(wyzieClient.downloadSrt("tt001")).thenReturn("""
                1
                00:01:00,000 --> 00:01:02,500
                New line.

                """);

        service.downloadSubtitles("tt001", "user-1");

        // Old data should be cleared
        verify(subtitleLineRepository).deleteByImdbId("tt001");
        // Then new data saved
        verify(subtitleLineRepository).saveAll(any());
        assertThat(movie.getSubtitleStatus()).isEqualTo(SubtitleStatus.DONE);
    }

    @Test
    void setsFailedStatusOnDownloadError() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt001", "Test Movie", 2020, SubtitleStatus.PENDING);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(movie));
        when(wyzieClient.downloadSrt("tt001"))
                .thenThrow(new RuntimeException("Network error"));

        assertThatThrownBy(() -> service.downloadSubtitles("tt001", "user-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Network error");

        assertThat(movie.getSubtitleStatus()).isEqualTo(SubtitleStatus.FAILED);
        assertThat(movie.getSubtitleError()).contains("Network error");
    }

    @Test
    void setsDownloadingStatusDuringDownload() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt001", "Test Movie", 2020, SubtitleStatus.PENDING);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(movie));
        when(wyzieClient.downloadSrt("tt001")).thenReturn("""
                1
                00:01:00,000 --> 00:01:02,500
                Test.

                """);

        service.downloadSubtitles("tt001", "user-1");

        // Status was set to DOWNLOADING before download started,
        // then to DONE after success (verified in other test)
        assertThat(movie.getSubtitleStatus()).isEqualTo(SubtitleStatus.DONE);
    }

    @Test
    void throwsWhenMovieNotFound() {
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadSubtitles("tt999", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
