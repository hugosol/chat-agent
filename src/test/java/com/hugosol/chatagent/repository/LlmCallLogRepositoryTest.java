package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.LlmCallLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class LlmCallLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LlmCallLogRepository repository;

    @Test
    void saveAndFindById_persistsAllFields() {
        LlmCallLog log = new LlmCallLog();
        log.setSessionId("session-1");
        log.setUserId("user-1");
        log.setAgentType("CONVERSATION");
        log.setMode("WORKPLACE_STANDUP");
        log.setModel("deepseek-v4-flash");
        log.setRequestPrompt("{\"text\":\"some prompt\"}");
        log.setResponseText("Some response text");
        log.setInputTokens(120);
        log.setOutputTokens(80);
        log.setDurationMs(1500L);
        log.setStatus("SUCCESS");
        entityManager.persistFlushFind(log);

        Optional<LlmCallLog> found = repository.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo("session-1");
        assertThat(found.get().getUserId()).isEqualTo("user-1");
        assertThat(found.get().getAgentType()).isEqualTo("CONVERSATION");
        assertThat(found.get().getMode()).isEqualTo("WORKPLACE_STANDUP");
        assertThat(found.get().getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(found.get().getRequestPrompt()).isEqualTo("{\"text\":\"some prompt\"}");
        assertThat(found.get().getResponseText()).isEqualTo("Some response text");
        assertThat(found.get().getInputTokens()).isEqualTo(120);
        assertThat(found.get().getOutputTokens()).isEqualTo(80);
        assertThat(found.get().getDurationMs()).isEqualTo(1500L);
        assertThat(found.get().getStatus()).isEqualTo("SUCCESS");
        assertThat(found.get().getCreateTime()).isNotNull();
    }

    @Test
    void saveAndFindById_errorRecordHasNullResponseAndTokens() {
        LlmCallLog log = new LlmCallLog();
        log.setModel("deepseek-v4-flash");
        log.setRequestPrompt("{\"text\":\"bad prompt\"}");
        log.setDurationMs(500L);
        log.setStatus("ERROR");
        log.setErrorMessage("Connection refused");
        entityManager.persistFlushFind(log);

        Optional<LlmCallLog> found = repository.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("ERROR");
        assertThat(found.get().getErrorMessage()).isEqualTo("Connection refused");
        assertThat(found.get().getResponseText()).isNull();
        assertThat(found.get().getInputTokens()).isNull();
        assertThat(found.get().getOutputTokens()).isNull();
    }

    @Test
    void deleteByCreateTimeBefore_removesOldRecords() {
        Instant now = Instant.now();

        LlmCallLog oldLog = new LlmCallLog();
        oldLog.setModel("deepseek-v4-flash");
        oldLog.setRequestPrompt("old");
        oldLog.setResponseText("old response");
        oldLog.setDurationMs(100L);
        oldLog.setStatus("SUCCESS");
        entityManager.persistFlushFind(oldLog);
        // Manually set create_time to 5 days ago via native query
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE llm_call_logs SET create_time = :cutoff WHERE id = :id")
                .setParameter("cutoff", now.minus(5, ChronoUnit.DAYS))
                .setParameter("id", oldLog.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        LlmCallLog recentLog = new LlmCallLog();
        recentLog.setModel("deepseek-v4-flash");
        recentLog.setRequestPrompt("recent");
        recentLog.setResponseText("recent response");
        recentLog.setDurationMs(100L);
        recentLog.setStatus("SUCCESS");
        entityManager.persistFlushFind(recentLog);

        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        repository.deleteByCreateTimeBefore(threeDaysAgo);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(oldLog.getId())).isEmpty();
        assertThat(repository.findById(recentLog.getId())).isPresent();
    }

    @Test
    void deleteByCreateTimeBefore_preservesRecentRecords() {
        LlmCallLog log = new LlmCallLog();
        log.setModel("deepseek-v4-flash");
        log.setRequestPrompt("test");
        log.setResponseText("test response");
        log.setDurationMs(100L);
        log.setStatus("SUCCESS");
        entityManager.persistFlushFind(log);

        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        repository.deleteByCreateTimeBefore(oneDayAgo);

        assertThat(repository.findById(log.getId())).isPresent();
    }
}
