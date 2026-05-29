package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.agent.common.TaskContext;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionComplete {

    private static final Logger log = LoggerFactory.getLogger(SessionComplete.class);

    private static final ReportResult FALLBACK_REPORT = new ReportResult(
            "Sorry, the session report generation failed. Your conversation and corrections have been saved.",
            "N/A", "N/A", -1, "N/A");

    private final SessionDbStore sessionStore;
    private final ReportAgent reportAgent;
    private final MemoryService memoryService;
    private final MemoryCueService memoryCueService;

    public SessionComplete(SessionDbStore sessionStore, ReportAgent reportAgent,
                           MemoryService memoryService, MemoryCueService memoryCueService) {
        this.sessionStore = sessionStore;
        this.reportAgent = reportAgent;
        this.memoryService = memoryService;
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

        if (userId != null) {
            memoryService.generateMemoryAsync(userId, report, mode, sessionId);
            memoryCueService.generateCuesAsync(sessionId, userId, mode, List.copyOf(messages));
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
