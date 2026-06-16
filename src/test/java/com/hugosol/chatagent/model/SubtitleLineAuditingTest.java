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
class SubtitleLineAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeAndUpdateTime() {
        SubtitleLine line = new SubtitleLine("tt1375666", "Inception", "00:01:00,000",
                "00:01:02,000", "Hello world.", "hello world", 1);

        SubtitleLine saved = entityManager.persistFlushFind(line);

        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }
}
