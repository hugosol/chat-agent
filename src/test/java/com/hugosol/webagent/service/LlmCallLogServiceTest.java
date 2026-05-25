package com.hugosol.webagent.service;

import com.hugosol.webagent.model.LlmCallLog;
import com.hugosol.webagent.repository.LlmCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmCallLogServiceTest {

    private LlmCallLogRepository repository;
    private ExecutorService executor;
    private LlmCallLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(LlmCallLogRepository.class);
        executor = Executors.newSingleThreadExecutor();
        service = new LlmCallLogService(repository, executor, "deepseek-v4-flash");
    }

    @Test
    void saveAsync_writesRecordWithAllFields() throws Exception {
        service.saveAsync("session-1", "user-1", "CONVERSATION", "WORKPLACE_STANDUP",
                "{\"messages\":[...]}", "Hello world", 100, 50, 1500L, "SUCCESS", null);
        Thread.sleep(200);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getAgentType()).isEqualTo("CONVERSATION");
        assertThat(saved.getMode()).isEqualTo("WORKPLACE_STANDUP");
        assertThat(saved.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(saved.getRequestPrompt()).isEqualTo("{\"messages\":[...]}");
        assertThat(saved.getResponseText()).isEqualTo("Hello world");
        assertThat(saved.getInputTokens()).isEqualTo(100);
        assertThat(saved.getOutputTokens()).isEqualTo(50);
        assertThat(saved.getDurationMs()).isEqualTo(1500L);
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void saveAsync_syncAgentHasNullMetadata() throws Exception {
        service.saveAsync(null, null, null, null,
                "{\"text\":\"prompt\"}", "response", null, null, 500L, "SUCCESS", null);
        Thread.sleep(200);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertThat(saved.getSessionId()).isNull();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getAgentType()).isNull();
        assertThat(saved.getMode()).isNull();
        assertThat(saved.getInputTokens()).isNull();
        assertThat(saved.getOutputTokens()).isNull();
        assertThat(saved.getRequestPrompt()).isEqualTo("{\"text\":\"prompt\"}");
    }

    @Test
    void saveAsync_errorRecordHasErrorMessage() throws Exception {
        service.saveAsync(null, null, null, null,
                "{\"text\":\"prompt\"}", null, null, null, 200L, "ERROR", "Timeout");
        Thread.sleep(200);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ERROR");
        assertThat(saved.getErrorMessage()).isEqualTo("Timeout");
        assertThat(saved.getResponseText()).isNull();
    }

    @Test
    void cleanupOnStartup_deletesRecordsOlderThanThreeDays() throws Exception {
        // cleanupOnStartup uses CompletableFuture.runAsync (ForkJoinPool), not the injected executor
        service.cleanupOnStartup();
        Thread.sleep(200);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreateTimeBefore(cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        // Should be approximately 3 days ago
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        assertThat(cutoff).isAfter(threeDaysAgo.minusMinutes(1));
        assertThat(cutoff).isBefore(threeDaysAgo.plusMinutes(1));
    }
}
