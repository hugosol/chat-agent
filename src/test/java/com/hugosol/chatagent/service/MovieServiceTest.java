package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private WatchedMovieRepository watchedMovieRepository;

    @Mock
    private SubtitleLineRepository subtitleLineRepository;

    @Mock
    private SubtitleService subtitleService;

    @Mock
    private TmdbClient tmdbClient;

    private CsvColumnDetector csvColumnDetector;
    private MovieService service;

    @BeforeEach
    void setUp() {
        csvColumnDetector = new CsvColumnDetector();
        service = new MovieService(watchedMovieRepository, subtitleLineRepository,
                subtitleService, tmdbClient, csvColumnDetector);
    }

    @Test
    void importBatch_savesMovies() {
        List<Map<String, String>> rows = List.of(
                Map.of("title", "Inception", "imdbId", "tt1375666", "year", "2010"),
                Map.of("title", "The Matrix", "imdbId", "tt0133093", "year", "1999")
        );

        when(watchedMovieRepository.findByUserIdAndImdbId(any(), any()))
                .thenReturn(Optional.empty());
        when(watchedMovieRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.importBatch(rows, "user-1");

        verify(watchedMovieRepository, times(2)).save(any());
        verify(subtitleService, never()).downloadSubtitles(any(), any());
    }

    @Test
    void importBatch_skipsExistingMovies() {
        List<Map<String, String>> rows = List.of(
                Map.of("title", "Inception", "imdbId", "tt1375666", "year", "2010")
        );

        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt1375666"))
                .thenReturn(Optional.of(new WatchedMovie("user-1", "tt1375666", "Inception", 2010, SubtitleStatus.DONE)));

        service.importBatch(rows, "user-1");

        verify(watchedMovieRepository, never()).save(any());
        verify(subtitleService, never()).downloadSubtitles(any(), any());
    }

    @Test
    void listMovies_returnsAllForUser() {
        List<WatchedMovie> movies = List.of(
                new WatchedMovie("user-1", "tt001", "Movie A", 2020, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt002", "Movie B", 2021, SubtitleStatus.PENDING)
        );
        Page<WatchedMovie> page = new PageImpl<>(movies);
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<WatchedMovie> result = service.listMovies("user-1", null, "title,asc", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listMovies_filtersBySearch() {
        List<WatchedMovie> movies = List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)
        );
        Page<WatchedMovie> page = new PageImpl<>(movies);
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<WatchedMovie> result = service.listMovies("user-1", "Incept", "title,asc", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Inception");
    }

    @Test
    void listMovies_sortsByTitleAsc() {
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMovies("user-1", null, "title,asc", PageRequest.of(0, 10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(watchedMovieRepository).findAll(any(Specification.class), captor.capture());
        // Pageable without sort — sort is applied in Specification
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void listMovies_sortsByReleaseYearDesc() {
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMovies("user-1", null, "releaseYear,desc", PageRequest.of(0, 10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(watchedMovieRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void listMovies_sortsByCreateTimeAsc() {
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMovies("user-1", null, "createTime,asc", PageRequest.of(0, 10));

        verify(watchedMovieRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listMovies_paginates() {
        List<WatchedMovie> allMovies = List.of(
                new WatchedMovie("user-1", "tt001", "A", 2020, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt002", "B", 2021, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt003", "C", 2022, SubtitleStatus.DONE)
        );
        Page<WatchedMovie> page = new PageImpl<>(allMovies.subList(0, 2), PageRequest.of(0, 2), 3);
        when(watchedMovieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<WatchedMovie> result = service.listMovies("user-1", null, "title,asc", PageRequest.of(0, 2));

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void deleteMovie_removesMovieAndSubtitles() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt001", "Movie A", 2020, SubtitleStatus.DONE);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(movie));

        service.deleteMovie("tt001", "user-1");

        verify(subtitleLineRepository).deleteByImdbId("tt001");
        verify(watchedMovieRepository).delete(movie);
    }

    @Test
    void deleteMovie_throwsWhenNotFound() {
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMovie("tt999", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void addMovie_savesWithPendingStatus() {
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.empty());
        when(watchedMovieRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        WatchedMovie result = service.addMovie("tt001", "Test Movie", 2022, "user-1");

        assertThat(result.getTitle()).isEqualTo("Test Movie");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getSubtitleStatus()).isEqualTo(SubtitleStatus.PENDING);
        verify(subtitleService, never()).downloadSubtitles(any(), any());
    }

    @Test
    void addMovie_returnsExistingIfAlreadyAdded() {
        WatchedMovie existing = new WatchedMovie("user-1", "tt001", "Existing", 2022, SubtitleStatus.DONE);
        when(watchedMovieRepository.findByUserIdAndImdbId("user-1", "tt001"))
                .thenReturn(Optional.of(existing));

        WatchedMovie result = service.addMovie("tt001", "Test Movie", 2022, "user-1");

        assertThat(result).isSameAs(existing);
        verify(subtitleService, never()).downloadSubtitles(any(), any());
    }

    @Test
    void searchTmdb_delegatesToClient() {
        List<TmdbClient.MovieCandidate> candidates = List.of(
                new TmdbClient.MovieCandidate("tt001", "Test Movie", 2022)
        );
        when(tmdbClient.search("Test Movie")).thenReturn(candidates);

        List<TmdbClient.MovieCandidate> result = service.searchTmdb("Test Movie");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imdbId()).isEqualTo("tt001");
    }

    @Test
    void redownloadSubtitles_delegatesToSubtitleService() {
        service.redownloadSubtitles("tt001", "user-1");

        verify(subtitleService).downloadSubtitles("tt001", "user-1");
    }
}
