package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.ReportAgent;
import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionComplete {

    private static final Logger log = LoggerFactory.getLogger(SessionComplete.class);

    private static final ReportResult FALLBACK_REPORT = new ReportResult(
            "Sorry, the session report generation failed. Your conversation and corrections have been saved.",
            "N/A", -1, "N/A");

    private final SessionDbStore sessionStore;
    private final ReportAgent reportAgent;
    private final LearningProfileService learningProfileService;
    private final AssertionService assertionService;

    public SessionComplete(SessionDbStore sessionStore, ReportAgent reportAgent,
                           LearningProfileService learningProfileService, AssertionService assertionService) {
        this.sessionStore = sessionStore;
        this.reportAgent = reportAgent;
        this.learningProfileService = learningProfileService;
        this.assertionService = assertionService;
    }

    public ReportResult complete(String sessionId, List<MessageData> messages,
                                  List<CorrectionData> corrections, String userId, AgentMode mode) {
        ReportResult report = generateReport(sessionId, userId, mode, messages, corrections);
        ReportResult forPersistence = report == FALLBACK_REPORT ? null : report;

        try {
            sessionStore.completeSession(sessionId, messages, corrections, forPersistence);
        } catch (Exception e) {
            log.error("Failed to persist session {}: {}", sessionId, e.getMessage());
        }

        if (userId != null && mode != AgentMode.JAPANESE_BUSINESS) {
            learningProfileService.generateLearningProfileAsync(userId, report, mode, sessionId);
            assertionService.generateAssertionsAsync(sessionId, userId, mode, List.copyOf(messages));
        }

        return report;
    }

    private ReportResult generateReport(String sessionId, String userId, AgentMode mode,
                                         List<MessageData> messages, List<CorrectionData> corrections) {
        try {
            return reportAgent.generate(messages, corrections,
                    new TaskContext(sessionId, userId, mode.name()));
        } catch (Exception e) {
            log.error("Report generation failed for session {}: {}", sessionId, e.getMessage());
            return FALLBACK_REPORT;
        }
    }
}
