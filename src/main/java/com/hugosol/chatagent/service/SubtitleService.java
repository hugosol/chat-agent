package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleLine;
import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates subtitle download, parsing, and persistence for a single movie.
 */
@Service
public class SubtitleService {

    private final SubtitleLineRepository subtitleLineRepository;
    private final WatchedMovieRepository watchedMovieRepository;
    private final SrtParser srtParser;
    private final WyzieClient wyzieClient;

    public SubtitleService(SubtitleLineRepository subtitleLineRepository,
                           WatchedMovieRepository watchedMovieRepository,
                           SrtParser srtParser,
                           WyzieClient wyzieClient) {
        this.subtitleLineRepository = subtitleLineRepository;
        this.watchedMovieRepository = watchedMovieRepository;
        this.srtParser = srtParser;
        this.wyzieClient = wyzieClient;
    }

    /**
     * Downloads subtitles for a movie and persists them as SubtitleLine rows.
     * Clears old subtitle data before downloading (safe for retry).
     *
     * @param imdbId the IMDB ID of the movie
     * @param userId the current user
     * @throws IllegalArgumentException if the movie is not found
     * @throws RuntimeException if the download fails
     */
    @Transactional
    public void downloadSubtitles(String imdbId, String userId) {
        WatchedMovie movie = watchedMovieRepository.findByUserIdAndImdbId(userId, imdbId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Watched movie not found: imdbId=" + imdbId + ", userId=" + userId));

        // Mark as downloading
        movie.setSubtitleStatus(SubtitleStatus.DOWNLOADING);
        watchedMovieRepository.save(movie);

        try {
            // Clear old subtitle data (safe for retry)
            subtitleLineRepository.deleteByImdbId(imdbId);

            // Download and parse
            String srtContent = wyzieClient.downloadSrt(imdbId);
            List<SubtitleLine> lines = srtParser.parse(srtContent, imdbId, movie.getTitle());

            // Persist
            subtitleLineRepository.saveAll(lines);

            // Update movie status
            movie.setSubtitleStatus(SubtitleStatus.DONE);
            movie.setSubtitleLineCount(lines.size());
            movie.setSubtitleError(null);
            watchedMovieRepository.save(movie);

        } catch (RuntimeException e) {
            // Mark as failed, preserve error message
            movie.setSubtitleStatus(SubtitleStatus.FAILED);
            movie.setSubtitleError(e.getMessage());
            watchedMovieRepository.save(movie);
            throw e;
        }
    }
}
