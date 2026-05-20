package com.hugosol.webagent.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerMessage.SessionStarted.class, name = "SESSION_STARTED"),
        @JsonSubTypes.Type(value = ServerMessage.AgentStreamDelta.class, name = "AGENT_STREAM_DELTA"),
        @JsonSubTypes.Type(value = ServerMessage.AgentStreamEnd.class, name = "AGENT_STREAM_END"),
        @JsonSubTypes.Type(value = ServerMessage.CorrectionResult.class, name = "CORRECTION_RESULT"),
        @JsonSubTypes.Type(value = ServerMessage.StateUpdate.class, name = "STATE_UPDATE"),
        @JsonSubTypes.Type(value = ServerMessage.TokenWarning.class, name = "TOKEN_WARNING"),
        @JsonSubTypes.Type(value = ServerMessage.SessionReportMessage.class, name = "SESSION_REPORT"),
        @JsonSubTypes.Type(value = ServerMessage.SessionResumed.class, name = "SESSION_RESUMED"),
        @JsonSubTypes.Type(value = ServerMessage.SessionHistory.class, name = "SESSION_HISTORY"),
        @JsonSubTypes.Type(value = ServerMessage.ErrorMessage.class, name = "ERROR")
})
public sealed interface ServerMessage
        permits ServerMessage.SessionStarted, ServerMessage.AgentStreamDelta, ServerMessage.AgentStreamEnd,
                ServerMessage.CorrectionResult, ServerMessage.StateUpdate, ServerMessage.TokenWarning,
                ServerMessage.SessionReportMessage, ServerMessage.SessionResumed, ServerMessage.SessionHistory,
                ServerMessage.ErrorMessage {

    record SessionStarted(String sessionId, String scenario, String persona) implements ServerMessage {
    }

    record AgentStreamDelta(String delta, int messageId) implements ServerMessage {
    }

    record AgentStreamEnd(String text, int messageId, double tokenUsage) implements ServerMessage {
    }

    record CorrectionResult(List<CorrectionData> corrections, int messageId) implements ServerMessage {
    }

    record StateUpdate(String state, double tokenUsage) implements ServerMessage {
    }

    record TokenWarning(double usage, String message) implements ServerMessage {
    }

    record ReportData(String summary, int fluencyScore, String errorSummary,
                      String vocabularySuggestions, String keyTakeaway) {
    }

    record SessionReportMessage(ReportData report) implements ServerMessage {
    }

    record SessionResumed(String sessionId, String scenario, String persona,
                          List<MessageData> messages, List<CorrectionData> corrections,
                          double tokenUsage) implements ServerMessage {
    }

    record SessionSummary(String id, String scenario, String startTime, String endTime, String status) {
    }

    record SessionHistory(List<SessionSummary> sessions) implements ServerMessage {
    }

    record ErrorMessage(String message) implements ServerMessage {
    }
}
