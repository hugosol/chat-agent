package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.WatchedMovie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchedMovieRepository extends JpaRepository<WatchedMovie, String> {

    List<WatchedMovie> findByUserId(String userId);

    Optional<WatchedMovie> findByUserIdAndImdbId(String userId, String imdbId);

    void deleteByImdbId(String imdbId);
}
