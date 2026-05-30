package com.hugosol.chatagent.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupLogoutHandler implements LogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupLogoutHandler.class);

    private final SessionService sessionService;

    public SessionCleanupLogoutHandler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            String userId = authentication.getName();
            sessionService.removeAllForUser(userId);
            log.info("Cleaned up all sessions for user: {}", userId);
        }
    }
}
