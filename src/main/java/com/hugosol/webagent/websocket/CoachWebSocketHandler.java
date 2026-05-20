package com.hugosol.webagent.websocket;

import com.hugosol.webagent.protocol.ClientMessage;
import com.hugosol.webagent.protocol.ProtocolDispatcher;
import com.hugosol.webagent.service.SessionStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CoachWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CoachWebSocketHandler.class);

    private final ProtocolDispatcher protocol;
    private final CoachMessageHandler messageHandler;
    private final SessionStateStore stateStore;

    public CoachWebSocketHandler(ProtocolDispatcher protocol, CoachMessageHandler messageHandler,
                                  SessionStateStore stateStore) {
        this.protocol = protocol;
        this.messageHandler = messageHandler;
        this.stateStore = stateStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        log.info("WebSocket connected: {}", wsSession.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        try {
            ClientMessage msg = protocol.parse(message.getPayload());
            protocol.dispatch(wsSession, msg, messageHandler);
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stateStore.unbind(session.getId());
    }
}
