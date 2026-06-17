package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
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
        }
    }

    public Page<WatchedMovie> listMovies(String userId, String search, String sortStr, Pageable pageable) {
        Pageable pageableWithoutSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Specification<WatchedMovie> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%"));
            }

            if (query != null) {
                Sort sort = parseSort(sortStr);
                if (sort.isSorted()) {
                    for (Sort.Order order : sort) {
                        if ("title".equals(order.getProperty())) {
                            query.orderBy(order.isAscending()
                                    ? cb.asc(cb.lower(root.get("title")))
                                    : cb.desc(cb.lower(root.get("title"))));
                        } else if ("releaseYear".equals(order.getProperty())) {
                            query.orderBy(order.isAscending()
                                    ? cb.asc(root.get("releaseYear"))
                                    : cb.desc(root.get("releaseYear")));
                        } else if ("createTime".equals(order.getProperty())) {
                            query.orderBy(order.isAscending()
                                    ? cb.asc(root.get("createTime"))
                                    : cb.desc(root.get("createTime")));
                        }
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return watchedMovieRepository.findAll(spec, pageableWithoutSort);
    }

    private Sort parseSort(String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
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

    /**
     * Saves a movie with PENDING status. Subtitle download is triggered manually
     * by the user via the retry/download endpoint.
     */
    public WatchedMovie addMovie(String imdbId, String title, Integer year, String userId) {
        return saveMovie(imdbId, title, year, userId);
    }

    @Transactional
    WatchedMovie saveMovie(String imdbId, String title, Integer year, String userId) {
        Optional<WatchedMovie> existing = watchedMovieRepository.findByUserIdAndImdbId(userId, imdbId);
        if (existing.isPresent()) {
            return existing.get();
        }
        WatchedMovie movie = new WatchedMovie(userId, imdbId, title, year, SubtitleStatus.PENDING);
        return watchedMovieRepository.save(movie);
    }

    @Transactional
    public void redownloadSubtitles(String imdbId, String userId) {
        subtitleService.downloadSubtitles(imdbId, userId);
    }
}
