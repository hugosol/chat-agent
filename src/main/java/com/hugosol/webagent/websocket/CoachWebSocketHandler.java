package com.hugosol.webagent.websocket;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import com.hugosol.webagent.model.PersonaType;
import com.hugosol.webagent.model.ScenarioType;
import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.service.GraphExecutionService;
import com.hugosol.webagent.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CoachWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CoachWebSocketHandler.class);
    private static final int TOKEN_LIMIT = 128000;
    private static final double WARN_RATIO = 0.8;

    private final GraphExecutionService graphService;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final Map<String, String> wsToSession = new ConcurrentHashMap<>();

    public CoachWebSocketHandler(GraphExecutionService graphService,
                                  SessionService sessionService,
                                  ObjectMapper objectMapper) {
        this.graphService = graphService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        log.info("WebSocket connected: {}", wsSession.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");

        switch (type) {
            case "START_SESSION"  -> handleStartSession(wsSession, msg);
            case "USER_INPUT"     -> handleUserInput(wsSession, msg);
            case "END_SESSION"    -> handleEndSession(wsSession);
            case "RESUME_SESSION" -> handleResumeSession(wsSession, msg);
            case "LOAD_HISTORY"   -> handleLoadHistory(wsSession);
            default -> sendError(wsSession, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = wsToSession.remove(session.getId());
        if (sessionId != null) {
            log.info("WebSocket disconnected (session {} kept alive for resume): {}", sessionId, session.getId());
        } else {
            log.info("WebSocket disconnected: {}", session.getId());
        }
    }

    private void handleStartSession(WebSocketSession wsSession, Map<String, Object> msg) throws IOException {
        String scenarioStr = (String) msg.getOrDefault("scenario", "WORKPLACE_STANDUP");
        String personaStr = (String) msg.getOrDefault("persona", "TEAM_COLLEAGUE");

        ScenarioType scenario;
        try {
            scenario = ScenarioType.valueOf(scenarioStr);
        } catch (IllegalArgumentException e) {
            sendError(wsSession, "Invalid scenario: " + scenarioStr);
            return;
        }

        PersonaType persona;
        try {
            persona = PersonaType.valueOf(personaStr);
        } catch (IllegalArgumentException e) {
            sendError(wsSession, "Invalid persona: " + personaStr);
            return;
        }

        Session session = sessionService.createSession(scenario, persona.name());
        graphService.initSession(session.getId(), scenario.name(), persona.name());
        wsToSession.put(wsSession.getId(), session.getId());

        log.info("Started session {} for WebSocket {}", session.getId(), wsSession.getId());

        sendMessage(wsSession, Map.of(
                "type", "SESSION_STARTED",
                "sessionId", session.getId(),
                "scenario", scenario.name(),
                "persona", persona.name()
        ));

    }

    private void handleUserInput(WebSocketSession wsSession, Map<String, Object> msg) throws IOException {
        String sessionId = wsToSession.get(wsSession.getId());
        if (sessionId == null) {
            sendError(wsSession, "No active session. Send START_SESSION first.");
            return;
        }

        String userInput = (String) msg.get("text");
        if (userInput == null || userInput.isBlank()) {
            sendError(wsSession, "Empty input text");
            return;
        }

        int messageId = msg.get("messageId") instanceof Number n ? n.intValue() : 0;

        sendStateUpdate(wsSession, "PROCESSING", tokenUsage(sessionId));

        graphService.processTurn(sessionId, userInput, messageId, new GraphExecutionService.TurnCallback() {
            @Override
            public void onConversationToken(String delta, int msgId) {
                sendSynced(wsSession, Map.of(
                        "type", "AGENT_STREAM_DELTA",
                        "delta", delta,
                        "messageId", msgId
                ));
            }

            @Override
            public void onConversationComplete(String fullText, int msgId, int tokenCount) {
                double usage = tokenUsage(sessionId);
                sendSynced(wsSession, Map.of(
                        "type", "AGENT_STREAM_END",
                        "text", fullText,
                        "messageId", msgId,
                        "tokenUsage", usage
                ));
                sendStateSynced(wsSession, "SPEAKING", usage);

                if (usage >= WARN_RATIO) {
                    sendSynced(wsSession, Map.of(
                            "type", "TOKEN_WARNING",
                            "usage", usage,
                            "message", "Approaching context limit. Consider ending the session soon."
                    ));
                }
            }

            @Override
            public void onCorrections(List<CorrectionData> corrections, int msgId) {
                if (!corrections.isEmpty()) {
                    sendSynced(wsSession, Map.of(
                            "type", "CORRECTION_RESULT",
                            "corrections", corrections,
                            "messageId", msgId
                    ));
                }
            }

            @Override
            public void onError(String errorMessage) {
                sendSynced(wsSession, Map.of(
                        "type", "ERROR",
                        "message", errorMessage
                ));
            }
        });
    }

    private void handleEndSession(WebSocketSession wsSession) throws IOException {
        String sessionId = wsToSession.get(wsSession.getId());
        if (sessionId == null) {
            sendError(wsSession, "No active session to end.");
            return;
        }

        sendStateUpdate(wsSession, "PROCESSING", tokenUsage(sessionId));

        try {
            CoachState state = graphService.getSessionState(sessionId);
            List<MessageData> messages = state != null ? new ArrayList<>(state.messages()) : List.of();
            List<CorrectionData> corrections = state != null ? new ArrayList<>(state.corrections()) : List.of();
            ReportResult report = graphService.generateReport(sessionId);

            sessionService.completeSession(sessionId, messages, corrections, report);

            graphService.removeSession(sessionId);
            wsToSession.remove(wsSession.getId());

            sendMessage(wsSession, Map.of(
                    "type", "SESSION_REPORT",
                    "report", Map.of(
                            "summary", report.overallAssessment(),
                            "fluencyScore", report.fluencyScore(),
                            "errorSummary", report.errorSummary(),
                            "vocabularySuggestions", report.vocabularySuggestions(),
                            "keyTakeaway", report.keyTakeaway()
                    )
            ));
        } catch (Exception e) {
            log.error("Error ending session", e);
            sendError(wsSession, "Error ending session: " + e.getMessage());
        }
    }

    private void handleResumeSession(WebSocketSession wsSession, Map<String, Object> msg) throws IOException {
        String sessionId = (String) msg.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sendError(wsSession, "Missing sessionId");
            return;
        }

        CoachState state = graphService.getSessionState(sessionId);
        if (state == null) {
            sendError(wsSession, "Session expired. Please start a new one.");
            return;
        }

        wsToSession.put(wsSession.getId(), sessionId);
        double usage = tokenUsage(sessionId);

        sendMessage(wsSession, Map.of(
                "type", "SESSION_RESUMED",
                "sessionId", sessionId,
                "scenario", state.scenario(),
                "persona", state.persona(),
                "messages", new ArrayList<>(state.messages()),
                "corrections", new ArrayList<>(state.corrections()),
                "tokenUsage", usage
        ));

        log.info("Resumed session {} for WebSocket {}", sessionId, wsSession.getId());
    }

    private void handleLoadHistory(WebSocketSession wsSession) throws IOException {
        List<Session> sessions = sessionService.getHistory();
        List<Map<String, Object>> history = sessions.stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "scenario", s.getScenario().name(),
                        "startTime", s.getStartTime().toString(),
                        "endTime", s.getEndTime() != null ? s.getEndTime().toString() : null,
                        "status", s.getStatus().name()
                ))
                .toList();

        sendMessage(wsSession, Map.of(
                "type", "SESSION_HISTORY",
                "sessions", history
        ));
    }

    private void sendMessage(WebSocketSession wsSession, Map<String, Object> payload) throws IOException {
        if (wsSession.isOpen()) {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (wsSession) {
                wsSession.sendMessage(new TextMessage(json));
            }
        }
    }

    private void sendSynced(WebSocketSession wsSession, Map<String, Object> payload) {
        try {
            sendMessage(wsSession, payload);
        } catch (IOException e) {
            log.error("Failed to send WS message", e);
        }
    }

    private void sendStateUpdate(WebSocketSession wsSession, String state, double tokenUsage) throws IOException {
        sendMessage(wsSession, Map.of(
                "type", "STATE_UPDATE",
                "state", state,
                "tokenUsage", tokenUsage
        ));
    }

    private void sendStateSynced(WebSocketSession wsSession, String state, double tokenUsage) {
        try {
            sendStateUpdate(wsSession, state, tokenUsage);
        } catch (IOException e) {
            log.error("Failed to send state update", e);
        }
    }

    private void sendError(WebSocketSession wsSession, String errorMessage) throws IOException {
        sendMessage(wsSession, Map.of(
                "type", "ERROR",
                "message", errorMessage
        ));
    }

    private double tokenUsage(String sessionId) {
        int tokens = graphService.getTokenCount(sessionId);
        return (double) tokens / TOKEN_LIMIT;
    }
}
