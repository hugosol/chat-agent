package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.LearningAgent;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.LearningType;
import com.hugosol.chatagent.model.UserLearningProfile;
import com.hugosol.chatagent.repository.UserLearningProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;

@Service
public class LearningProfileService {

    private static final Logger log = LoggerFactory.getLogger(LearningProfileService.class);

    private final LearningAgent learningAgent;
    private final UserLearningProfileRepository repository;
    private final ExecutorService executor;
    private final TransactionTemplate transactionTemplate;

    public LearningProfileService(LearningAgent learningAgent,
                                  UserLearningProfileRepository repository,
                                  @Qualifier("llmRequestExecutor") ExecutorService executor,
                                  TransactionTemplate transactionTemplate) {
        this.learningAgent = learningAgent;
        this.repository = repository;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
    }

    public void generateLearningProfileAsync(String userId, ReportResult report, AgentMode mode) {
        generateLearningProfileAsync(userId, report, mode, null);
    }

    public void generateLearningProfileAsync(String userId, ReportResult report, AgentMode mode, String sessionId) {
        executor.execute(() -> generateSingle(userId, LearningType.LEARNING_PROFILE, null, sessionId,
                () -> learningAgent.mergeProfile(
                        loadLatestContent(userId, LearningType.LEARNING_PROFILE, null),
                        report.errorSummary(),
                        new TaskContext(sessionId, userId, null))));
    }

    public String loadLatestContent(String userId, LearningType type, AgentMode mode) {
        return repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(userId, type, mode)
                .map(UserLearningProfile::getContent)
                .orElse("");
    }

    public String loadLatestContent(String userId, String type, AgentMode mode) {
        return loadLatestContent(userId, LearningType.valueOf(type), mode);
    }

    private void generateSingle(String userId, LearningType type, AgentMode mode, String sessionId, MemoryGenerateTask task) {
        try {
            UserLearningProfile latest = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(userId, type, mode).orElse(null);
            log.info("LearningProfileService generating {} for user {}, mode={}, oldVersion={}, oldContent={}",
                    type, userId, mode, latest != null ? latest.getVersion() : 0,
                    latest != null ? latest.getContent() : "(first session)");
            String merged = task.generate();
            int newVersion = (latest != null) ? latest.getVersion() + 1 : 1;

            transactionTemplate.executeWithoutResult(status -> {
                UserLearningProfile memory = new UserLearningProfile(userId, type, merged, newVersion, mode, sessionId);
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
