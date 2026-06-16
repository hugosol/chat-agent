package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates movie management: CSV import, TMDB search, CRUD, and subtitle trigger.
 */
@Service
public class MovieService {

    private static final Logger log = LoggerFactory.getLogger(MovieService.class);

    private final WatchedMovieRepository watchedMovieRepository;
    private final SubtitleLineRepository subtitleLineRepository;
    private final SubtitleService subtitleService;
    private final TmdbClient tmdbClient;
    private final CsvColumnDetector csvColumnDetector;

    public MovieService(WatchedMovieRepository watchedMovieRepository,
                        SubtitleLineRepository subtitleLineRepository,
                        SubtitleService subtitleService,
                        TmdbClient tmdbClient,
                        CsvColumnDetector csvColumnDetector) {
        this.watchedMovieRepository = watchedMovieRepository;
        this.subtitleLineRepository = subtitleLineRepository;
        this.subtitleService = subtitleService;
        this.tmdbClient = tmdbClient;
        this.csvColumnDetector = csvColumnDetector;
    }

    /**
     * Imports a batch of movies from CSV-parsed rows.
     * Each row is a map of column name → value. Column detection is handled by CsvColumnDetector.
     * Subtitle download is triggered for each new movie. Failures on individual movies do not stop the batch.
     */
    @Transactional
    public void importBatch(List<Map<String, String>> rows, String userId) {
        for (Map<String, String> row : rows) {
            String imdbId = row.get("imdbId");
            String title = row.get("title");
            String yearStr = row.get("year");
            Integer year = null;
            if (yearStr != null && !yearStr.isBlank()) {
                try {
                    year = Integer.parseInt(yearStr.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid year value for movie {}: {}", title, yearStr);
                }
            }

            if (imdbId == null || imdbId.isBlank() || title == null || title.isBlank()) {
                log.warn("Skipping row with missing imdbId or title: {}", row);
                continue;
            }

            // Skip if already exists
            if (watchedMovieRepository.findByUserIdAndImdbId(userId, imdbId).isPresent()) {
                log.debug("Movie already imported: {}", imdbId);
                continue;
            }

            WatchedMovie movie = new WatchedMovie(userId, imdbId, title, year, SubtitleStatus.PENDING);
            watchedMovieRepository.save(movie);

            // Trigger subtitle download (best-effort, failure on one doesn't stop others)
            try {
                subtitleService.downloadSubtitles(imdbId, userId);
            } catch (Exception e) {
                log.error("Subtitle download failed for {} ({}): {}", title, imdbId, e.getMessage());
            }
        }
    }

    public List<WatchedMovie> listMovies(String userId) {
        return watchedMovieRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteMovie(String imdbId, String userId) {
        WatchedMovie movie = watchedMovieRepository.findByUserIdAndImdbId(userId, imdbId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Movie not found: imdbId=" + imdbId));
        subtitleLineRepository.deleteByImdbId(imdbId);
        watchedMovieRepository.delete(movie);
    }

    public List<TmdbClient.MovieCandidate> searchTmdb(String query) {
        return tmdbClient.search(query);
    }

    @Transactional
    public WatchedMovie addMovie(String imdbId, String title, Integer year, String userId) {
        Optional<WatchedMovie> existing = watchedMovieRepository.findByUserIdAndImdbId(userId, imdbId);
        if (existing.isPresent()) {
            return existing.get();
        }

        WatchedMovie movie = new WatchedMovie(userId, imdbId, title, year, SubtitleStatus.PENDING);
        movie = watchedMovieRepository.save(movie);

        try {
            subtitleService.downloadSubtitles(imdbId, userId);
        } catch (Exception e) {
            log.error("Subtitle download failed for new movie {} ({}): {}", title, imdbId, e.getMessage());
        }

        return movie;
    }

    @Transactional
    public void redownloadSubtitles(String imdbId, String userId) {
        subtitleService.downloadSubtitles(imdbId, userId);
    }
}
