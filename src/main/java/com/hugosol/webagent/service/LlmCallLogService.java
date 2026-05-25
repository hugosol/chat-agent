package com.hugosol.webagent.service;

import com.hugosol.webagent.model.LlmCallLog;
import com.hugosol.webagent.repository.LlmCallLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class LlmCallLogService {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLogService.class);

    private final LlmCallLogRepository repository;
    private final ExecutorService llmLogExecutor;
    private final String modelName;

    public LlmCallLogService(LlmCallLogRepository repository,
                             @org.springframework.beans.factory.annotation.Qualifier("llmLogExecutor") ExecutorService llmLogExecutor,
                             @org.springframework.beans.factory.annotation.Value("${langchain4j.openai.chat-model.model-name}") String modelName) {
        this.repository = repository;
        this.llmLogExecutor = llmLogExecutor;
        this.modelName = modelName;
    }

    public void saveAsync(String sessionId, String userId, String agentType, String mode,
                          String requestPrompt, String responseText,
                          Integer inputTokens, Integer outputTokens,
                          Long durationMs, String status, String errorMessage) {
        llmLogExecutor.execute(() -> {
            LlmCallLog logEntry = new LlmCallLog();
            logEntry.setSessionId(sessionId);
            logEntry.setUserId(userId);
            logEntry.setAgentType(agentType);
            logEntry.setMode(mode);
            logEntry.setModel(modelName);
            logEntry.setRequestPrompt(requestPrompt);
            logEntry.setResponseText(responseText);
            logEntry.setInputTokens(inputTokens);
            logEntry.setOutputTokens(outputTokens);
            logEntry.setDurationMs(durationMs);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(errorMessage);
            repository.save(logEntry);
        });
    }

    @PostConstruct
    void cleanupOnStartup() {
        CompletableFuture.runAsync(() -> {
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
            log.info("Cleaning up LLM call logs older than {}", threeDaysAgo);
            repository.deleteByCreateTimeBefore(threeDaysAgo);
        });
    }
}
