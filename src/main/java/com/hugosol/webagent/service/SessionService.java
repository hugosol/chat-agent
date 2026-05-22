package com.hugosol.webagent.service;

import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentType;
import com.hugosol.webagent.model.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Map<String, CoachState> activeStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToWs = new ConcurrentHashMap<>();
    private final TokenTracker tokenTracker;

    public SessionService(TokenTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    public void init(String sessionId, String scenario, String persona, String userId, String wsId) {
        Map<String, Object> initData = CoachState.initialState(sessionId, scenario, persona, userId);
        var state = new CoachState(initData);
        activeStates.put(sessionId, state);
        tokenTracker.initSession(sessionId);
        sessionToWs.put(sessionId, wsId);
        log.info("SessionService: initialized session {}", sessionId);
    }

    public boolean exists(String sessionId) {
        return activeStates.containsKey(sessionId);
    }

    public void remove(String sessionId) {
        activeStates.remove(sessionId);
        tokenTracker.removeSession(sessionId);
        sessionToWs.remove(sessionId);
        log.info("SessionService: removed session {}", sessionId);
    }

    public void bind(String wsId, String sessionId) {
        sessionToWs.put(sessionId, wsId);
        log.info("SessionService: bound WebSocket {} to session {}", wsId, sessionId);
    }

    public void unbind(String wsId) {
        sessionToWs.values().removeIf(wsId::equals);
        log.info("SessionService: unbound WebSocket {}", wsId);
    }

    public String getSessionId(String wsId) {
        for (var entry : sessionToWs.entrySet()) {
            if (entry.getValue().equals(wsId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getWsForSession(String sessionId) {
        return sessionToWs.get(sessionId);
    }

    public String getUserId(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? state.userId() : null;
    }

    public void removeAllForUser(String userId) {
        activeStates.entrySet().removeIf(entry -> {
            if (userId.equals(entry.getValue().userId())) {
                String sessionId = entry.getKey();
                tokenTracker.removeSession(sessionId);
                sessionToWs.remove(sessionId);
                return true;
            }
            return false;
        });
        log.info("SessionService: removed all sessions for user {}", userId);
    }

    public void addMessage(String sessionId, MessageRole role, String content, int messageId, Integer tokenCount) {
        CoachState state = activeStates.get(sessionId);
        if (state != null) {
            synchronized (state) {
                MessageData md = new MessageData(role, content, messageId);
                md.setTokenCount(tokenCount);
                state.addMessage(md);
            }
        }
    }

    public void addCorrections(String sessionId, List<CorrectionData> corrections) {
        CoachState state = activeStates.get(sessionId);
        if (state != null) {
            synchronized (state) {
                state.addCorrections(corrections);
            }
        }
    }

    public void recordTokens(String sessionId, AgentType agentType, int count) {
        tokenTracker.addTokens(sessionId, agentType, count);
    }

    public List<MessageData> getMessages(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? new ArrayList<>(state.messages()) : List.of();
    }

    public List<CorrectionData> getCorrections(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? new ArrayList<>(state.corrections()) : List.of();
    }

    public String getScenario(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? state.scenario() : "";
    }

    public String getPersona(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? state.persona() : "";
    }

    public int getCorrectionCount(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        return state != null ? state.corrections().size() : 0;
    }

    public double getUsageRatio(String sessionId) {
        return tokenTracker.getUsageRatio(sessionId, AgentType.CONVERSATION);
    }

    public boolean isTokenWarning(String sessionId) {
        return tokenTracker.isWarning(sessionId, AgentType.CONVERSATION);
    }
}
