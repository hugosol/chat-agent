package com.hugosol.chatagent.model;

import com.hugosol.chatagent.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class WatchedMovieAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeAndUpdateTime() {
        WatchedMovie movie = new WatchedMovie("user-1", "tt1375666", "Inception", 2010,
                SubtitleStatus.PENDING);

        WatchedMovie saved = entityManager.persistFlushFind(movie);

        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }
}
