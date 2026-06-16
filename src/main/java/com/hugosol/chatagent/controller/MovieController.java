package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.service.MovieService;
import com.hugosol.chatagent.service.TmdbClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/movies")
    public ResponseEntity<List<Map<String, Object>>> listMovies() {
        String userId = getUserId();
        List<WatchedMovie> movies = movieService.listMovies(userId);
        List<Map<String, Object>> result = movies.stream()
                .map(this::movieToMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/movies/import/batch")
    public ResponseEntity<Map<String, Object>> importBatch(@RequestBody List<Map<String, String>> rows) {
        String userId = getUserId();
        movieService.importBatch(rows, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("imported", rows.size());
        response.put("status", "ok");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/movies/{imdbId}")
    public ResponseEntity<Map<String, Object>> deleteMovie(@PathVariable String imdbId) {
        String userId = getUserId();
        movieService.deleteMovie(imdbId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "deleted");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/movies/search")
    public ResponseEntity<List<Map<String, Object>>> searchMovies(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<TmdbClient.MovieCandidate> candidates = movieService.searchTmdb(query);
        List<Map<String, Object>> result = candidates.stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("imdbId", c.imdbId());
                    map.put("title", c.title());
                    map.put("year", c.year());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/movies")
    public ResponseEntity<Map<String, Object>> addMovie(@RequestBody Map<String, Object> body) {
        String userId = getUserId();
        String imdbId = (String) body.get("imdbId");
        String title = (String) body.get("title");
        Integer year = body.get("year") != null ? ((Number) body.get("year")).intValue() : null;

        if (imdbId == null || imdbId.isBlank() || title == null || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        WatchedMovie movie = movieService.addMovie(imdbId, title, year, userId);
        return ResponseEntity.ok(movieToMap(movie));
    }

    @PostMapping("/movies/{imdbId}/download")
    public ResponseEntity<Map<String, Object>> redownloadSubtitles(@PathVariable String imdbId) {
        String userId = getUserId();
        movieService.redownloadSubtitles(imdbId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "download_triggered");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> movieToMap(WatchedMovie movie) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", movie.getId());
        map.put("imdbId", movie.getImdbId());
        map.put("title", movie.getTitle());
        map.put("year", movie.getReleaseYear());
        map.put("subtitleStatus", movie.getSubtitleStatus().name());
        map.put("subtitleLineCount", movie.getSubtitleLineCount());
        map.put("subtitleError", movie.getSubtitleError());
        map.put("createTime", movie.getCreateTime() != null ? movie.getCreateTime().toString() : null);
        return map;
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
