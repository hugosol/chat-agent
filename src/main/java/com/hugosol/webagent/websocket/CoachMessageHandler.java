package com.hugosol.webagent.websocket;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.protocol.ClientMessage;
import com.hugosol.webagent.protocol.MessageHandler;
import com.hugosol.webagent.protocol.ProtocolDispatcher;
import com.hugosol.webagent.protocol.ServerMessage;
import com.hugosol.webagent.service.SessionComplete;
import com.hugosol.webagent.service.SessionStore;
import com.hugosol.webagent.service.SessionService;
import com.hugosol.webagent.service.TurnProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CoachMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CoachMessageHandler.class);

    private final SessionService sessionService;
    private final TurnProcessor turnProcessor;
    private final SessionComplete sessionComplete;
    private final SessionStore sessionStore;
    private final ProtocolDispatcher protocol;

    public CoachMessageHandler(SessionService sessionService,
                               TurnProcessor turnProcessor,
                               SessionComplete sessionComplete,
                               SessionStore sessionStore,
                               ProtocolDispatcher protocol) {
        this.sessionService = sessionService;
        this.turnProcessor = turnProcessor;
        this.sessionComplete = sessionComplete;
        this.sessionStore = sessionStore;
        this.protocol = protocol;
    }

    private String requireUserId(WebSocketSession ws) {
        Principal principal = ws.getPrincipal();
        if (principal == null) {
            log.warn("No principal on WebSocket session {} — using anonymous", ws.getId());
            return "anonymous";
        }
        return principal.getName();
    }

    @Override
    public void onStartSession(WebSocketSession ws, ClientMessage.StartSession msg) throws IOException {
        String userId = requireUserId(ws);

        String modeStr = msg.mode() != null ? msg.mode() : "WORKPLACE_STANDUP";

        AgentMode mode;
        try {
            mode = AgentMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            String available = Arrays.stream(AgentMode.values())
                    .map(AgentMode::name)
                    .collect(Collectors.joining(", "));
            protocol.send(ws, new ServerMessage.ErrorMessage(
                    "Invalid mode: " + modeStr + ". Available: [" + available + "]"));
            return;
        }

        Session session = sessionStore.createSession(mode, userId);
        sessionService.init(session.getId(), mode.name(), userId, ws.getId());

        log.info("Started session {} for user {}", session.getId(), userId);

        protocol.send(ws, new ServerMessage.SessionStarted(
                session.getId(), mode.name()));
    }

    @Override
    public void onUserInput(WebSocketSession ws, ClientMessage.UserInput msg) throws IOException {
        String sessionId = sessionService.getSessionId(ws.getId());
        if (sessionId == null) {
            protocol.send(ws, new ServerMessage.ErrorMessage("No active session."));
            return;
        }

        String userInput = msg.text();
        if (userInput == null || userInput.isBlank()) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Empty message."));
            return;
        }

        int messageId = msg.messageId();

        protocol.send(ws, new ServerMessage.StateUpdate("PROCESSING",
                sessionService.getUsageRatio(sessionId)));

        turnProcessor.processTurn(sessionId, userInput, messageId, new TurnProcessor.TurnCallback() {
            @Override
            public void onConversationToken(String delta, int msgId) {
                protocol.sendSynced(ws, new ServerMessage.AgentStreamDelta(delta, msgId));
            }

            @Override
            public void onConversationComplete(String fullText, int msgId, int tokenCount) {
                double usage = sessionService.getUsageRatio(sessionId);
                protocol.sendSynced(ws, new ServerMessage.AgentStreamEnd(fullText, msgId, usage));
                protocol.sendSynced(ws, new ServerMessage.StateUpdate("SPEAKING", usage));

                if (sessionService.isTokenWarning(sessionId)) {
                    protocol.sendSynced(ws, new ServerMessage.TokenWarning(usage,
                            "Approaching context limit. Consider ending the session soon."));
                }
            }

            @Override
            public void onCorrections(List<CorrectionData> corrections, int msgId) {
                if (!corrections.isEmpty()) {
                    protocol.sendSynced(ws, new ServerMessage.CorrectionResult(corrections, msgId));
                }
            }

            @Override
            public void onError(String errorMessage) {
                protocol.sendSynced(ws, new ServerMessage.ErrorMessage(errorMessage));
            }
        });
    }

    @Override
    public void onEndSession(WebSocketSession ws) throws IOException {
        long startTime = System.currentTimeMillis();

        String sessionId = sessionService.getSessionId(ws.getId());
        if (sessionId == null) {
            protocol.send(ws, new ServerMessage.ErrorMessage("No active session to end."));
            return;
        }

        protocol.send(ws, new ServerMessage.StateUpdate("PROCESSING",
                sessionService.getUsageRatio(sessionId)));

        sessionService.waitForPendingCorrections(sessionId, 10_000);
        List<MessageData> messages = sessionService.getMessages(sessionId);
        List<CorrectionData> corrections = sessionService.getCorrections(sessionId);
        String userId = sessionService.getUserId(sessionId);
        AgentMode mode = AgentMode.valueOf(sessionService.getMode(sessionId));

        ReportResult report = sessionComplete.complete(sessionId, messages, corrections, userId, mode);

        sessionService.remove(sessionId);

        var reportData = new ServerMessage.ReportData(
                report != null ? report.overallAssessment() : "",
                report != null ? report.topicSummary() : "",
                report != null ? report.fluencyScore() : 0,
                report != null ? report.errorSummary() : "",
                report != null ? report.keyTakeaway() : ""
        );
        protocol.send(ws, new ServerMessage.SessionReportMessage(reportData));

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Conversation close latency: {}s", String.format("%.1f", elapsed / 1000.0));
    }

    @Override
    public void onResumeSession(WebSocketSession ws, ClientMessage.ResumeSession msg) throws IOException {
        String userId = requireUserId(ws);

        String sessionId = msg.sessionId();
        if (!sessionService.exists(sessionId)) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Session not found"));
            return;
        }

        String ownerId = sessionService.getUserId(sessionId);
        if (ownerId != null && !ownerId.equals(userId)) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Session not found"));
            log.warn("User {} attempted to resume session {} owned by {}", userId, sessionId, ownerId);
            return;
        }

        String oldWsId = sessionService.getWsForSession(sessionId);
        sessionService.bind(ws.getId(), sessionId);

        double usage = sessionService.getUsageRatio(sessionId);

        protocol.send(ws, new ServerMessage.SessionResumed(
                sessionId,
                sessionService.getMode(sessionId),
                sessionService.getMessages(sessionId),
                sessionService.getCorrections(sessionId),
                usage
        ));

        log.info("Resumed session {} for user {}", sessionId, userId);
    }

    @Override
    public void onLoadHistory(WebSocketSession ws) throws IOException {
        String userId = requireUserId(ws);
        List<Session> sessions = sessionStore.getHistory(userId);
        List<ServerMessage.SessionSummary> history = sessions.stream()
                .map(s -> new ServerMessage.SessionSummary(
                        s.getId(),
                        s.getMode().name(),
                        s.getStartTime().toString(),
                        s.getEndTime() != null ? s.getEndTime().toString() : null,
                        s.getStatus().name()
                ))
                .toList();

        protocol.send(ws, new ServerMessage.SessionHistory(history));
    }
}
