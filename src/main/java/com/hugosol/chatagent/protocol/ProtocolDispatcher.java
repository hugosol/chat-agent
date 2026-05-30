package com.hugosol.chatagent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class ProtocolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProtocolDispatcher.class);

    private final ObjectMapper objectMapper;

    public ProtocolDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ClientMessage parse(String json) throws IOException {
        return objectMapper.readValue(json, ClientMessage.class);
    }

    public String serialize(ServerMessage message) throws IOException {
        return objectMapper.writeValueAsString(message);
    }

    public void dispatch(WebSocketSession ws, ClientMessage msg, MessageHandler handler) throws IOException {
        if (msg instanceof ClientMessage.StartSession m) {
            handler.onStartSession(ws, m);
        } else if (msg instanceof ClientMessage.UserInput m) {
            handler.onUserInput(ws, m);
        } else if (msg instanceof ClientMessage.EndSession) {
            handler.onEndSession(ws);
        } else if (msg instanceof ClientMessage.ResumeSession m) {
            handler.onResumeSession(ws, m);
        } else if (msg instanceof ClientMessage.LoadHistory) {
            handler.onLoadHistory(ws);
        }
    }

    public void send(WebSocketSession wsSession, ServerMessage message) throws IOException {
        if (wsSession.isOpen()) {
            String json = serialize(message);
            synchronized (wsSession) {
                wsSession.sendMessage(new TextMessage(json));
            }
        }
    }

    public void sendSynced(WebSocketSession wsSession, ServerMessage message) {
        try {
            send(wsSession, message);
        } catch (IOException e) {
            log.error("Failed to send WS message", e);
        }
    }
}
