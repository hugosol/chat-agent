package com.hugosol.webagent.websocket;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.PersonaType;
import com.hugosol.webagent.model.ScenarioType;
import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.protocol.ClientMessage;
import com.hugosol.webagent.protocol.MessageHandler;
import com.hugosol.webagent.protocol.ProtocolDispatcher;
import com.hugosol.webagent.protocol.ServerMessage;
import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.service.SessionStore;
import com.hugosol.webagent.service.SessionService;
import com.hugosol.webagent.service.TurnProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Component
public class CoachMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CoachMessageHandler.class);

    private final SessionService sessionService;
    private final TurnProcessor turnProcessor;
    private final ReportAgent reportAgent;
    private final SessionStore sessionStore;
    private final ProtocolDispatcher protocol;

    public CoachMessageHandler(SessionService sessionService,
                               TurnProcessor turnProcessor,
                               ReportAgent reportAgent,
                               SessionStore sessionStore,
                               ProtocolDispatcher protocol) {
        this.sessionService = sessionService;
        this.turnProcessor = turnProcessor;
        this.reportAgent = reportAgent;
        this.sessionStore = sessionStore;
        this.protocol = protocol;
    }

    @Override
    public void onStartSession(WebSocketSession ws, ClientMessage.StartSession msg) throws IOException {
        String scenarioStr = msg.scenario() != null ? msg.scenario() : "WORKPLACE_STANDUP";
        String personaStr = msg.persona() != null ? msg.persona() : "TEAM_COLLEAGUE";

        ScenarioType scenario;
        try {
            scenario = ScenarioType.valueOf(scenarioStr);
        } catch (IllegalArgumentException e) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Invalid scenario: " + scenarioStr));
            return;
        }

        PersonaType persona;
        try {
            persona = PersonaType.valueOf(personaStr);
        } catch (IllegalArgumentException e) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Invalid persona: " + personaStr));
            return;
        }

        Session session = sessionStore.createSession(scenario, persona.name());
        sessionService.init(session.getId(), scenario.name(), persona.name(), ws.getId());

        log.info("Started session {} for WebSocket {}", session.getId(), ws.getId());

        protocol.send(ws, new ServerMessage.SessionStarted(
                session.getId(), scenario.name(), persona.name()));
    }

    @Override
    public void onUserInput(WebSocketSession ws, ClientMessage.UserInput msg) throws IOException {
        String sessionId = sessionService.getSessionId(ws.getId());
        if (sessionId == null) {
            protocol.send(ws, new ServerMessage.ErrorMessage("No active session. Send START_SESSION first."));
            return;
        }

        String userInput = msg.text();
        if (userInput == null || userInput.isBlank()) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Empty input text"));
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
        String sessionId = sessionService.getSessionId(ws.getId());
        if (sessionId == null) {
            protocol.send(ws, new ServerMessage.ErrorMessage("No active session to end."));
            return;
        }

        protocol.send(ws, new ServerMessage.StateUpdate("PROCESSING",
                sessionService.getUsageRatio(sessionId)));

        try {
            List<MessageData> messages = sessionService.getMessages(sessionId);
            List<CorrectionData> corrections = sessionService.getCorrections(sessionId);
            ReportResult report = reportAgent.generate(messages, corrections);

            sessionStore.completeSession(sessionId, messages, corrections, report);

            sessionService.remove(sessionId);

            var reportData = new ServerMessage.ReportData(
                    report.overallAssessment(),
                    report.fluencyScore(),
                    report.errorSummary(),
                    report.vocabularySuggestions(),
                    report.keyTakeaway()
            );
            protocol.send(ws, new ServerMessage.SessionReportMessage(reportData));
        } catch (Exception e) {
            log.error("Error ending session", e);
            protocol.send(ws, new ServerMessage.ErrorMessage("Error ending session: " + e.getMessage()));
        }
    }

    @Override
    public void onResumeSession(WebSocketSession ws, ClientMessage.ResumeSession msg) throws IOException {
        String sessionId = msg.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Missing sessionId"));
            return;
        }

        if (!sessionService.exists(sessionId)) {
            protocol.send(ws, new ServerMessage.ErrorMessage("Session expired. Please start a new one."));
            return;
        }

        sessionService.bind(ws.getId(), sessionId);
        double usage = sessionService.getUsageRatio(sessionId);

        protocol.send(ws, new ServerMessage.SessionResumed(
                sessionId,
                sessionService.getScenario(sessionId),
                sessionService.getPersona(sessionId),
                sessionService.getMessages(sessionId),
                sessionService.getCorrections(sessionId),
                usage
        ));

        log.info("Resumed session {} for WebSocket {}", sessionId, ws.getId());
    }

    @Override
    public void onLoadHistory(WebSocketSession ws) throws IOException {
        List<Session> sessions = sessionStore.getHistory();
        List<ServerMessage.SessionSummary> history = sessions.stream()
                .map(s -> new ServerMessage.SessionSummary(
                        s.getId(),
                        s.getScenario().name(),
                        s.getStartTime().toString(),
                        s.getEndTime() != null ? s.getEndTime().toString() : null,
                        s.getStatus().name()
                ))
                .toList();

        protocol.send(ws, new ServerMessage.SessionHistory(history));
    }
}
