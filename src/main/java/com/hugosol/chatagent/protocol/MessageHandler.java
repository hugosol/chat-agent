package com.hugosol.chatagent.protocol;

import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public interface MessageHandler {

    void onStartSession(WebSocketSession ws, ClientMessage.StartSession msg) throws IOException;

    void onUserInput(WebSocketSession ws, ClientMessage.UserInput msg) throws IOException;

    void onEndSession(WebSocketSession ws) throws IOException;

    void onResumeSession(WebSocketSession ws, ClientMessage.ResumeSession msg) throws IOException;

    void onLoadHistory(WebSocketSession ws) throws IOException;
}
