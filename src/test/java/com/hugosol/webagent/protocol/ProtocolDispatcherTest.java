package com.hugosol.webagent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolDispatcherTest {

    @Mock
    private WebSocketSession wsSession;

    @Mock
    private MessageHandler handler;

    private ProtocolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        dispatcher = new ProtocolDispatcher(mapper);
    }

    @Test
    void parseStartSessionMessage() throws IOException {
        String json = "{\"type\":\"START_SESSION\",\"mode\":\"WORKPLACE_STANDUP\"}";

        ClientMessage msg = dispatcher.parse(json);

        assertThat(msg).isInstanceOf(ClientMessage.StartSession.class);
        ClientMessage.StartSession start = (ClientMessage.StartSession) msg;
        assertThat(start.mode()).isEqualTo("WORKPLACE_STANDUP");
    }

    @Test
    void parseUserInputMessage() throws IOException {
        String json = "{\"type\":\"USER_INPUT\",\"text\":\"Hello\",\"messageId\":1}";

        ClientMessage msg = dispatcher.parse(json);

        assertThat(msg).isInstanceOf(ClientMessage.UserInput.class);
        ClientMessage.UserInput input = (ClientMessage.UserInput) msg;
        assertThat(input.text()).isEqualTo("Hello");
        assertThat(input.messageId()).isEqualTo(1);
    }

    @Test
    void parseEndSessionMessage() throws IOException {
        String json = "{\"type\":\"END_SESSION\"}";

        ClientMessage msg = dispatcher.parse(json);

        assertThat(msg).isInstanceOf(ClientMessage.EndSession.class);
    }

    @Test
    void parseResumeSessionMessage() throws IOException {
        String json = "{\"type\":\"RESUME_SESSION\",\"sessionId\":\"abc-123\"}";

        ClientMessage msg = dispatcher.parse(json);

        assertThat(msg).isInstanceOf(ClientMessage.ResumeSession.class);
        ClientMessage.ResumeSession resume = (ClientMessage.ResumeSession) msg;
        assertThat(resume.sessionId()).isEqualTo("abc-123");
    }

    @Test
    void parseLoadHistoryMessage() throws IOException {
        String json = "{\"type\":\"LOAD_HISTORY\"}";

        ClientMessage msg = dispatcher.parse(json);

        assertThat(msg).isInstanceOf(ClientMessage.LoadHistory.class);
    }

    @Test
    void serializeServerMessageIncludesTypeField() throws IOException {
        ServerMessage msg = new ServerMessage.SessionStarted("s1", "WORKPLACE_STANDUP");

        String json = dispatcher.serialize(msg);

        assertThat(json).contains("\"type\"");
        assertThat(json).contains("\"SESSION_STARTED\"");
        assertThat(json).contains("\"s1\"");
    }

    @Test
    void dispatchStartSessionCallsHandler() throws IOException {
        ClientMessage.StartSession msg = new ClientMessage.StartSession("WORKPLACE_STANDUP");

        dispatcher.dispatch(wsSession, msg, handler);

        verify(handler).onStartSession(wsSession, msg);
    }

    @Test
    void dispatchUserInputCallsHandler() throws IOException {
        ClientMessage.UserInput msg = new ClientMessage.UserInput("Hi", 1);

        dispatcher.dispatch(wsSession, msg, handler);

        verify(handler).onUserInput(wsSession, msg);
    }

    @Test
    void dispatchEndSessionCallsHandler() throws IOException {
        ClientMessage.EndSession msg = new ClientMessage.EndSession();

        dispatcher.dispatch(wsSession, msg, handler);

        verify(handler).onEndSession(wsSession);
    }

    @Test
    void dispatchResumeSessionCallsHandler() throws IOException {
        ClientMessage.ResumeSession msg = new ClientMessage.ResumeSession("s1");

        dispatcher.dispatch(wsSession, msg, handler);

        verify(handler).onResumeSession(wsSession, msg);
    }

    @Test
    void sendWhenSessionIsOpen() throws IOException {
        when(wsSession.isOpen()).thenReturn(true);

        dispatcher.send(wsSession, new ServerMessage.SessionStarted("s1", "W"));

        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendWhenSessionIsClosedDoesNothing() throws IOException {
        when(wsSession.isOpen()).thenReturn(false);

        dispatcher.send(wsSession, new ServerMessage.SessionStarted("s1", "W"));

        verify(wsSession, never()).sendMessage(any());
    }

    @Test
    void sendSyncedCatchesIOException() throws IOException {
        when(wsSession.isOpen()).thenReturn(true);
        doThrow(new IOException("connection lost")).when(wsSession).sendMessage(any());

        dispatcher.sendSynced(wsSession, new ServerMessage.SessionStarted("s1", "W"));
    }

    @Test
    void errorMessageSerializesCorrectly() throws IOException {
        ServerMessage msg = new ServerMessage.ErrorMessage("Something went wrong");

        String json = dispatcher.serialize(msg);

        assertThat(json).contains("\"type\":\"ERROR\"");
        assertThat(json).contains("\"Something went wrong\"");
    }

    @Test
    void serverMessageTypeIsIncludedInAllMessages() throws IOException {
        List<ServerMessage> messages = List.of(
                new ServerMessage.StateUpdate("PROCESSING", 0.5),
                new ServerMessage.AgentStreamDelta("Hi", 1),
                new ServerMessage.AgentStreamEnd("Hi there", 1, 0.3),
                new ServerMessage.TokenWarning(0.8, "warning text"),
                new ServerMessage.CorrectionResult(List.of(), 1),
                new ServerMessage.SessionReportMessage(
                        new ServerMessage.ReportData("a", "topics", 5, "b", "c", "d")),
                new ServerMessage.SessionResumed("s1", "W", List.of(), List.of(), 0.0),
                new ServerMessage.SessionHistory(List.of()),
                new ServerMessage.ErrorMessage("err")
        );

        for (ServerMessage msg : messages) {
            String json = dispatcher.serialize(msg);
            assertThat(json).contains("\"type\"");
        }
    }
}
