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
class MessageAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeOnPersist() {
        Message message = new Message("s1", MessageRole.USER, "Hello", 1, null);

        Message saved = entityManager.persistFlushFind(message);

        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void shouldNotHaveLegacyTimestampField() {
        Message message = new Message("s1", MessageRole.USER, "Hello", 1, null);

        Message saved = entityManager.persistFlushFind(message);

        assertThat(saved.getCreateTime()).isNotNull();
    }
}
