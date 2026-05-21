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
class SessionAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeAndKeepBusinessTimestamps() {
        Session session = new Session(ScenarioType.WORKPLACE_STANDUP, "TEAM_COLLEAGUE");

        Session saved = entityManager.persistFlushFind(session);

        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getStartTime()).isNotNull();
        assertThat(saved.getEndTime()).isNull();
    }
}
