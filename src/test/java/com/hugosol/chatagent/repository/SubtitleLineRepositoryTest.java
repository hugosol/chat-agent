package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.SubtitleLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class SubtitleLineRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubtitleLineRepository repository;

    @Test
    void saveAndFindById_persistsCorrectly() {
        SubtitleLine line = new SubtitleLine("tt1375666", "Inception", "00:05:30,000",
                "00:05:33,000", "You mustn't be afraid to dream a little bigger, darling.",
                "you mustnt be afraid to dream a little bigger darling", 42);
        entityManager.persistFlushFind(line);

        var found = repository.findById(line.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getImdbId()).isEqualTo("tt1375666");
        assertThat(found.get().getLineIndex()).isEqualTo(42);
        assertThat(found.get().getWordsLower()).contains("dream");
    }

    @Test
    void findByImdbIdInAndWordsLowerLike_matchesWord() {
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:01:00,000", "00:01:02,000",
                "The weather is nice today.", "the weather is nice today", 1));
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:02:00,000", "00:02:02,000",
                "I dream of electric sheep.", "i dream of electric sheep", 2));
        entityManager.persistFlushFind(new SubtitleLine("tt002", "Film B", "00:01:00,000", "00:01:02,000",
                "Sweet dreams are made of this.", "sweet dreams are made of this", 1));

        List<SubtitleLine> results = repository.findByImdbIdInAndWordsLowerLike(
                List.of("tt001"), "% dream %");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("I dream of electric sheep.");
        assertThat(results.get(0).getLineIndex()).isEqualTo(2);
    }

    @Test
    void findByImdbIdInAndWordsLowerLike_ordersByImdbIdAndLineIndex() {
        entityManager.persistFlushFind(new SubtitleLine("tt002", "Film B", "00:02:00,000", "00:02:02,000",
                "Hello world.", "hello world", 2));
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:02:00,000", "00:02:02,000",
                "Hello world.", "hello world", 2));
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:01:00,000", "00:01:02,000",
                "Hello world.", "hello world", 1));

        List<SubtitleLine> results = repository.findByImdbIdInAndWordsLowerLike(
                List.of("tt001", "tt002"), "%hello%");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getImdbId()).isEqualTo("tt001");
        assertThat(results.get(0).getLineIndex()).isEqualTo(1);
        assertThat(results.get(1).getImdbId()).isEqualTo("tt001");
        assertThat(results.get(1).getLineIndex()).isEqualTo(2);
        assertThat(results.get(2).getImdbId()).isEqualTo("tt002");
    }

    @Test
    void findByImdbIdInAndWordsLowerLike_returnsEmptyWhenNoMatch() {
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:01:00,000", "00:01:02,000",
                "Hello world.", "hello world", 1));

        List<SubtitleLine> results = repository.findByImdbIdInAndWordsLowerLike(
                List.of("tt001"), "%nonexistent%");
        assertThat(results).isEmpty();
    }

    @Test
    void countByImdbId_returnsCorrectCount() {
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:01:00,000", "00:01:02,000",
                "Line one.", "line one", 1));
        entityManager.persistFlushFind(new SubtitleLine("tt001", "Film A", "00:02:00,000", "00:02:02,000",
                "Line two.", "line two", 2));
        entityManager.persistFlushFind(new SubtitleLine("tt002", "Film B", "00:01:00,000", "00:01:02,000",
                "Another.", "another", 1));

        assertThat(repository.countByImdbId("tt001")).isEqualTo(2);
        assertThat(repository.countByImdbId("tt002")).isEqualTo(1);
        assertThat(repository.countByImdbId("tt999")).isZero();
    }
}
