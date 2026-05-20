package com.hugosol.webagent.websocket;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import com.hugosol.webagent.model.ScenarioType;
import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.service.GraphExecutionService;
import com.hugosol.webagent.service.GraphExecutionService.TurnResult;
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
            case "START_SESSION" -> handleStartSession(wsSession, msg);
            case "USER_INPUT" -> handleUserInput(wsSession, msg);
            case "END_SESSION" -> handleEndSession(wsSession);
            case "LOAD_HISTORY" -> handleLoadHistory(wsSession);
            default -> sendError(wsSession, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = wsToSession.remove(session.getId());
        if (sessionId != null) {
            graphService.removeSession(sessionId);
        }
        log.info("WebSocket disconnected: {}", session.getId());
    }

    private void handleStartSession(WebSocketSession wsSession, Map<String, Object> msg) throws IOException {
        String scenarioStr = (String) msg.getOrDefault("scenario", "WORKPLACE_STANDUP");
        String persona = (String) msg.getOrDefault("persona", "TEAM_COLLEAGUE");

        ScenarioType scenario;
        try {
            scenario = ScenarioType.valueOf(scenarioStr);
        } catch (IllegalArgumentException e) {
            sendError(wsSession, "Invalid scenario: " + scenarioStr);
            return;
        }

        Session session = sessionService.createSession(scenario, persona);
        graphService.initSession(session.getId(), scenario.name(), persona);
        wsToSession.put(wsSession.getId(), session.getId());

        log.info("Started session {} for WebSocket {}", session.getId(), wsSession.getId());

        sendMessage(wsSession, Map.of(
                "type", "SESSION_STARTED",
                "sessionId", session.getId(),
                "scenario", scenario.name(),
                "persona", persona
        ));

        sendStateUpdate(wsSession, "LISTENING", 0.0);
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

        sendStateUpdate(wsSession, "PROCESSING", tokenUsage(sessionId));

        try {
            TurnResult result = graphService.processTurn(sessionId, userInput);

            double usage = tokenUsage(sessionId);

            sendMessage(wsSession, Map.of(
                    "type", "AGENT_RESPONSE",
                    "conversationText", result.mergedResponse(),
                    "corrections", result.corrections(),
                    "tokenUsage", usage
            ));

            sendStateUpdate(wsSession, "SPEAKING", usage);

            if (usage >= WARN_RATIO) {
                sendMessage(wsSession, Map.of(
                        "type", "TOKEN_WARNING",
                        "usage", usage,
                        "message", "Approaching context limit. Consider ending the session soon."
                ));
            }
        } catch (Exception e) {
            log.error("Error processing turn", e);
            sendError(wsSession, "Processing error: " + e.getMessage());
        }
    }

    private void handleEndSession(WebSocketSession wsSession) throws IOException {
        String sessionId = wsToSession.get(wsSession.getId());
        if (sessionId == null) {
            sendError(wsSession, "No active session to end.");
            return;
        }

        sendStateUpdate(wsSession, "PROCESSING", tokenUsage(sessionId));

        try {
            List<MessageData> messages = graphService.getSessionMessages(sessionId);
            List<CorrectionData> corrections = graphService.getSessionCorrections(sessionId);
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
            wsSession.sendMessage(new TextMessage(json));
        }
    }

    private void sendStateUpdate(WebSocketSession wsSession, String state, double tokenUsage) throws IOException {
        sendMessage(wsSession, Map.of(
                "type", "STATE_UPDATE",
                "state", state,
                "tokenUsage", tokenUsage
        ));
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
