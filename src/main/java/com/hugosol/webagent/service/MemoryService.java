package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.MemoryAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
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

    public void generateMemoryAsync(String userId, ReportResult report) {
        executor.execute(() -> generateSingle(userId, MemoryType.TOPIC_SUMMARY,
                () -> memoryAgent.mergeTopic(
                        loadLatestContent(userId, MemoryType.TOPIC_SUMMARY),
                        report.topicSummary())));
        executor.execute(() -> generateSingle(userId, MemoryType.LEARNING_PROFILE,
                () -> memoryAgent.mergeProfile(
                        loadLatestContent(userId, MemoryType.LEARNING_PROFILE),
                        report.errorSummary(),
                        report.vocabularySuggestions())));
    }

    public String loadLatestContent(String userId, MemoryType type) {
        return repository.findTopByUserIdAndTypeOrderByVersionDesc(userId, type)
                .map(UserMemory::getContent)
                .orElse("");
    }

    public String loadLatestContent(String userId, String type) {
        return loadLatestContent(userId, MemoryType.valueOf(type));
    }

    private void generateSingle(String userId, MemoryType type, MemoryGenerateTask task) {
        try {
            UserMemory latest = repository.findTopByUserIdAndTypeOrderByVersionDesc(userId, type).orElse(null);
            log.info("MemoryService generating {} for user {}, oldVersion={}, oldContent={}",
                    type, userId, latest != null ? latest.getVersion() : 0,
                    latest != null ? latest.getContent() : "(first session)");
            String merged = task.generate();
            int newVersion = (latest != null) ? latest.getVersion() + 1 : 1;

            transactionTemplate.executeWithoutResult(status -> {
                UserMemory memory = new UserMemory(userId, type, merged, newVersion);
                repository.save(memory);
                log.info("Saved {} v{} for user {}", type, newVersion, userId);
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
