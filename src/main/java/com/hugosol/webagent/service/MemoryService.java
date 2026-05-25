package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.MemoryAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryType;
import com.hugosol.webagent.model.UserMemory;
import com.hugosol.webagent.repository.UserMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryAgent memoryAgent;
    private final UserMemoryRepository repository;
    private final ExecutorService executor;
    private final TransactionTemplate transactionTemplate;

    public MemoryService(MemoryAgent memoryAgent,
                         UserMemoryRepository repository,
                         @Qualifier("memoryExecutor") ExecutorService executor,
                         TransactionTemplate transactionTemplate) {
        this.memoryAgent = memoryAgent;
        this.repository = repository;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
    }

    public void generateMemoryAsync(String userId, ReportResult report, AgentMode mode) {
        generateMemoryAsync(userId, report, mode, null);
    }

    public void generateMemoryAsync(String userId, ReportResult report, AgentMode mode, String sessionId) {
        executor.execute(() -> generateSingle(userId, MemoryType.TOPIC_SUMMARY, mode, sessionId,
                () -> memoryAgent.mergeTopic(
                        loadLatestContent(userId, MemoryType.TOPIC_SUMMARY, mode),
                        report.topicSummary())));
        executor.execute(() -> generateSingle(userId, MemoryType.LEARNING_PROFILE, null, sessionId,
                () -> memoryAgent.mergeProfile(
                        loadLatestContent(userId, MemoryType.LEARNING_PROFILE, null),
                        report.errorSummary())));
    }

    public String loadLatestContent(String userId, MemoryType type, AgentMode mode) {
        return repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(userId, type, mode)
                .map(UserMemory::getContent)
                .orElse("");
    }

    public String loadLatestContent(String userId, String type, AgentMode mode) {
        return loadLatestContent(userId, MemoryType.valueOf(type), mode);
    }

    private void generateSingle(String userId, MemoryType type, AgentMode mode, String sessionId, MemoryGenerateTask task) {
        try {
            UserMemory latest = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(userId, type, mode).orElse(null);
            log.info("MemoryService generating {} for user {}, mode={}, oldVersion={}, oldContent={}",
                    type, userId, mode, latest != null ? latest.getVersion() : 0,
                    latest != null ? latest.getContent() : "(first session)");
            String merged = task.generate();
            int newVersion = (latest != null) ? latest.getVersion() + 1 : 1;

            transactionTemplate.executeWithoutResult(status -> {
                UserMemory memory = new UserMemory(userId, type, merged, newVersion, mode, sessionId);
                repository.save(memory);
                log.info("Saved {} v{} for user {}, mode={}", type, newVersion, userId, mode);
            });
        } catch (Exception e) {
            log.warn("Failed to generate {} for user {}: {}", type, userId, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface MemoryGenerateTask {
        String generate() throws Exception;
    }
}
