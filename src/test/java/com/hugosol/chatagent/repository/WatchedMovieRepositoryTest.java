package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class WatchedMovieRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WatchedMovieRepository repository;

    @Test
    void saveAndFindById_persistsCorrectly() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt1375666", "Inception", 2010, SubtitleStatus.DONE);
        entityManager.persistFlushFind(movie);

        var found = repository.findById(movie.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getImdbId()).isEqualTo("tt1375666");
        assertThat(found.get().getTitle()).isEqualTo("Inception");
        assertThat(found.get().getSubtitleStatus()).isEqualTo(SubtitleStatus.DONE);
    }

    @Test
    void findByUserId_returnsUserMovies() {
        entityManager.persistFlushFind(new WatchedMovie("user-a", "tt001", "Movie 1", 2020, SubtitleStatus.DONE));
        entityManager.persistFlushFind(new WatchedMovie("user-a", "tt002", "Movie 2", 2021, SubtitleStatus.PENDING));
        entityManager.persistFlushFind(new WatchedMovie("user-b", "tt003", "Movie 3", 2022, SubtitleStatus.DONE));

        List<WatchedMovie> results = repository.findByUserId("user-a");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(WatchedMovie::getImdbId).containsExactly("tt001", "tt002");
    }

    @Test
    void findByUserIdAndImdbId_returnsCorrectMovie() {
        entityManager.persistFlushFind(new WatchedMovie("user-1", "tt1375666", "Inception", 2010, SubtitleStatus.DONE));
        entityManager.persistFlushFind(new WatchedMovie("user-1", "tt0133093", "The Matrix", 1999, SubtitleStatus.DONE));

        Optional<WatchedMovie> found = repository.findByUserIdAndImdbId("user-1", "tt1375666");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Inception");
    }

    @Test
    void findByUserIdAndImdbId_returnsEmptyForWrongUser() {
        entityManager.persistFlushFind(new WatchedMovie("user-1", "tt1375666", "Inception", 2010, SubtitleStatus.DONE));

        Optional<WatchedMovie> found = repository.findByUserIdAndImdbId("user-2", "tt1375666");
        assertThat(found).isEmpty();
    }
}
