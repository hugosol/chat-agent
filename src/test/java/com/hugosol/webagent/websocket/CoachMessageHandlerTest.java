package com.hugosol.webagent.websocket;

import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.model.ErrorType;
import com.hugosol.webagent.model.ScenarioType;
import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.protocol.ClientMessage;
import com.hugosol.webagent.protocol.ProtocolDispatcher;
import com.hugosol.webagent.protocol.ServerMessage;
import com.hugosol.webagent.service.SessionService;
import com.hugosol.webagent.service.SessionStore;
import com.hugosol.webagent.service.TurnProcessor;
import com.hugosol.webagent.service.TurnProcessor.TurnCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachMessageHandlerTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private TurnProcessor turnProcessor;

    @Mock
    private ReportAgent reportAgent;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private ProtocolDispatcher protocol;

    @Mock
    private WebSocketSession ws;

    private CoachMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CoachMessageHandler(sessionService, turnProcessor,
                reportAgent, sessionStore, protocol);
    }

    @Test
    void onStartSessionCreatesSessionAndSendsStarted() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        Session session = new Session(ScenarioType.WORKPLACE_STANDUP, "TEAM_COLLEAGUE");
        when(sessionStore.createSession(any(), anyString())).thenReturn(session);

        handler.onStartSession(ws, new ClientMessage.StartSession("WORKPLACE_STANDUP", "TEAM_COLLEAGUE"));

        verify(sessionService).init(session.getId(), "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.SessionStarted.class);
    }

    @Test
    void onStartSessionWithNullValuesUsesDefaults() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        Session session = new Session(ScenarioType.WORKPLACE_STANDUP, "TEAM_COLLEAGUE");
        when(sessionStore.createSession(any(), anyString())).thenReturn(session);

        handler.onStartSession(ws, new ClientMessage.StartSession(null, null));

        verify(sessionService).init(session.getId(), "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
    }

    @Test
    void onStartSessionInvalidScenarioSendsError() throws IOException {
        handler.onStartSession(ws, new ClientMessage.StartSession("INVALID", "TEAM_COLLEAGUE"));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.ErrorMessage.class);
    }

    @Test
    void onUserInputNoSessionSendsError() throws IOException {
        handler.onUserInput(ws, new ClientMessage.UserInput("hi", 1));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.ErrorMessage.class);
    }

    @Test
    void onUserInputBlankSendsError() throws IOException {
        when(sessionService.getSessionId(null)).thenReturn("s1");

        handler.onUserInput(ws, new ClientMessage.UserInput("   ", 1));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.ErrorMessage.class);
    }

    @Test
    void onUserInputSendsProcessingAndProcessesTurn() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getUsageRatio("s1")).thenReturn(0.3);
        doAnswer(inv -> {
            TurnCallback cb = inv.getArgument(3);
            cb.onConversationToken("Hi", 1);
            cb.onConversationComplete("Hi there", 1, 100);
            return null;
        }).when(turnProcessor).processTurn(eq("s1"), eq("hello"), eq(1), any());

        handler.onUserInput(ws, new ClientMessage.UserInput("hello", 1));

        verify(protocol).send(ws, new ServerMessage.StateUpdate("PROCESSING", 0.3));
        verify(protocol).sendSynced(ws, new ServerMessage.AgentStreamDelta("Hi", 1));
        verify(protocol).sendSynced(ws, new ServerMessage.AgentStreamEnd("Hi there", 1, 0.3));
        verify(protocol).sendSynced(ws, new ServerMessage.StateUpdate("SPEAKING", 0.3));
    }

    @Test
    void onUserInputTokenWarningFiresWhenOverLimit() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getUsageRatio("s1")).thenReturn(0.85);
        when(sessionService.isTokenWarning("s1")).thenReturn(true);
        doAnswer(inv -> {
            TurnCallback cb = inv.getArgument(3);
            cb.onConversationComplete("ok", 1, 100);
            return null;
        }).when(turnProcessor).processTurn(eq("s1"), anyString(), anyInt(), any());

        handler.onUserInput(ws, new ClientMessage.UserInput("test", 1));

        verify(protocol).sendSynced(ws,
                new ServerMessage.TokenWarning(0.85, "Approaching context limit. Consider ending the session soon."));
    }

    @Test
    void onUserInputCorrectionsReachClient() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getUsageRatio("s1")).thenReturn(0.1);

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "orig", "corr", "expl");
        List<CorrectionData> corrs = List.of(cd);

        doAnswer(inv -> {
            TurnCallback cb = inv.getArgument(3);
            cb.onCorrections(corrs, 1);
            cb.onConversationComplete("ok", 1, 10);
            return null;
        }).when(turnProcessor).processTurn(eq("s1"), anyString(), anyInt(), any());

        handler.onUserInput(ws, new ClientMessage.UserInput("test", 1));

        verify(protocol).sendSynced(ws, new ServerMessage.CorrectionResult(corrs, 1));
    }

    @Test
    void onUserInputEmptyCorrectionsNotSent() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getUsageRatio("s1")).thenReturn(0.1);

        doAnswer(inv -> {
            TurnCallback cb = inv.getArgument(3);
            cb.onCorrections(List.of(), 1);
            cb.onConversationComplete("ok", 1, 10);
            return null;
        }).when(turnProcessor).processTurn(eq("s1"), anyString(), anyInt(), any());

        handler.onUserInput(ws, new ClientMessage.UserInput("test", 1));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol, atLeastOnce()).sendSynced(eq(ws), captor.capture());
        for (ServerMessage msg : captor.getAllValues()) {
            assertThat(msg).isNotInstanceOf(ServerMessage.CorrectionResult.class);
        }
    }

    @Test
    void onEndSessionCompletesAndSendsReport() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getMessages("s1")).thenReturn(List.of());
        when(sessionService.getCorrections("s1")).thenReturn(List.of());
        when(sessionService.getUsageRatio("s1")).thenReturn(0.2);

        ReportResult reportResult = new ReportResult("Great", "none", "try however", 8, "practice");
        when(reportAgent.generate(any(), any())).thenReturn(reportResult);

        handler.onEndSession(ws);

        verify(sessionStore).completeSession(eq("s1"), any(), any(), eq(reportResult));
        verify(sessionService).remove("s1");

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol, atLeastOnce()).send(eq(ws), captor.capture());
        ServerMessage last = captor.getValue();
        assertThat(last).isInstanceOf(ServerMessage.SessionReportMessage.class);
    }

    @Test
    void onEndSessionNoSessionSendsError() throws IOException {
        handler.onEndSession(ws);

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.ErrorMessage.class);
    }

    @Test
    void onEndSessionErrorDuringReportSendsError() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.getSessionId("ws1")).thenReturn("s1");
        when(sessionService.getMessages("s1")).thenReturn(List.of());
        when(sessionService.getCorrections("s1")).thenReturn(List.of());
        when(sessionService.getUsageRatio("s1")).thenReturn(0.2);
        when(reportAgent.generate(any(), any())).thenThrow(new RuntimeException("report failed"));

        handler.onEndSession(ws);

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol, atLeastOnce()).send(eq(ws), captor.capture());
        ServerMessage last = captor.getValue();
        assertThat(last).isInstanceOf(ServerMessage.ErrorMessage.class);
        assertThat(((ServerMessage.ErrorMessage) last).message()).contains("report failed");
    }

    @Test
    void onResumeSessionSuccessfullyResumes() throws IOException {
        when(ws.getId()).thenReturn("ws1");
        when(sessionService.exists("s1")).thenReturn(true);
        when(sessionService.getUsageRatio("s1")).thenReturn(0.1);
        when(sessionService.getScenario("s1")).thenReturn("WORKPLACE_STANDUP");
        when(sessionService.getPersona("s1")).thenReturn("TEAM_COLLEAGUE");
        when(sessionService.getMessages("s1")).thenReturn(List.of());
        when(sessionService.getCorrections("s1")).thenReturn(List.of());

        handler.onResumeSession(ws, new ClientMessage.ResumeSession("s1"));

        verify(sessionService).bind("ws1", "s1");

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.SessionResumed.class);
    }

    @Test
    void onResumeSessionMissingIdSendsError() throws IOException {
        handler.onResumeSession(ws, new ClientMessage.ResumeSession(null));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.ErrorMessage.class);
    }

    @Test
    void onResumeSessionExpiredSendsError() throws IOException {
        when(sessionService.exists("expired")).thenReturn(false);

        handler.onResumeSession(ws, new ClientMessage.ResumeSession("expired"));

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        ServerMessage msg = captor.getValue();
        assertThat(msg).isInstanceOf(ServerMessage.ErrorMessage.class);
        assertThat(((ServerMessage.ErrorMessage) msg).message()).contains("expired");
    }

    @Test
    void onLoadHistoryReturnsSessionSummaries() throws IOException {
        Session s1 = new Session(ScenarioType.WORKPLACE_STANDUP, "TEAM_COLLEAGUE");
        when(sessionStore.getHistory()).thenReturn(List.of(s1));

        handler.onLoadHistory(ws);

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.SessionHistory.class);
    }

    @Test
    void onLoadHistoryEmptyReturnsEmptyList() throws IOException {
        when(sessionStore.getHistory()).thenReturn(List.of());

        handler.onLoadHistory(ws);

        ArgumentCaptor<ServerMessage> captor = ArgumentCaptor.forClass(ServerMessage.class);
        verify(protocol).send(eq(ws), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ServerMessage.SessionHistory.class);
    }
}
