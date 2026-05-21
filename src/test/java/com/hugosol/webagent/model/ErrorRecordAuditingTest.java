package com.hugosol.webagent.model;

import com.hugosol.webagent.config.JpaConfig;
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
class ErrorRecordAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeOnPersist() {
        ErrorRecord record = new ErrorRecord("s1", "m1", ErrorType.GRAMMAR,
                "he go", "he goes", "第三人称");

        ErrorRecord saved = entityManager.persistFlushFind(record);

        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void updateTimeShouldBeSetOnInitialPersist() {
        ErrorRecord record = new ErrorRecord("s1", "m1", ErrorType.GRAMMAR,
                "he go", "he goes", "第三人称");

        ErrorRecord saved = entityManager.persistFlushFind(record);

        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isEqualTo(saved.getCreateTime());
    }
}
