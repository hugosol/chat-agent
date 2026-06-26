package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.MemoryCueAgent;
import com.hugosol.chatagent.agent.ReportAgent;
import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
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
    private final MemoryCueAgent memoryCueAgent;
    private final MemoryCueService memoryCueService;

    public SessionComplete(SessionDbStore sessionStore, ReportAgent reportAgent,
                           LearningProfileService learningProfileService, AssertionService assertionService,
                           MemoryCueAgent memoryCueAgent, MemoryCueService memoryCueService) {
        this.sessionStore = sessionStore;
        this.reportAgent = reportAgent;
        this.learningProfileService = learningProfileService;
        this.assertionService = assertionService;
        this.memoryCueAgent = memoryCueAgent;
        this.memoryCueService = memoryCueService;
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
            TaskContext ctx = new TaskContext(sessionId, userId, mode.name());
            List<Integer> switchPoints = memoryCueAgent.detectSwitches(messages, mode, ctx);
            List<List<MessageData>> segments = splitBySwitches(messages, switchPoints);

            learningProfileService.generateLearningProfileAsync(userId, report, mode, sessionId);
            assertionService.generateAssertionsAsync(sessionId, userId, mode, segments);
            memoryCueService.generateCuesAsync(sessionId, userId, mode, segments);
        }

        return report;
    }

    static List<List<MessageData>> splitBySwitches(List<MessageData> messages, List<Integer> switchPoints) {
        if (switchPoints == null || switchPoints.isEmpty()) {
            return messages.isEmpty() ? Collections.emptyList() : List.of(messages);
        }
        List<List<MessageData>> segments = new ArrayList<>();
        int startIdx = 0;
        for (int switchIdx : switchPoints) {
            int endIdx = Math.min(switchIdx + 1, messages.size());
            if (startIdx < messages.size()) {
                segments.add(new ArrayList<>(messages.subList(startIdx, endIdx)));
            }
            startIdx = endIdx;
        }
        if (startIdx < messages.size()) {
            segments.add(new ArrayList<>(messages.subList(startIdx, messages.size())));
        }
        return segments;
    }

    static String buildLabeledMessages(List<MessageData> messages) {
        StringBuilder labeled = new StringBuilder();
        for (MessageData msg : messages) {
            labeled.append("[MSG#").append(msg.getMessageId()).append("] ")
                    .append(msg.getRole().name()).append(": ")
                    .append(msg.getContent()).append("\n");
        }
        return labeled.toString();
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
