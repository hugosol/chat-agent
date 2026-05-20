package com.hugosol.webagent.config;

import com.hugosol.webagent.websocket.CoachWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CoachWebSocketHandler coachWebSocketHandler;

    public WebSocketConfig(CoachWebSocketHandler coachWebSocketHandler) {
        this.coachWebSocketHandler = coachWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(coachWebSocketHandler, "/ws/coach")
                .setAllowedOrigins("*");
    }
}
