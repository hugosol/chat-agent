package com.hugosol.webagent.service;

import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
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
public class SessionStateStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStateStore.class);

    private final Map<String, CoachState> activeStates = new ConcurrentHashMap<>();
    private final TokenTracker tokenTracker;

    public SessionStateStore(TokenTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    public void init(String sessionId, String scenario, String persona) {
        Map<String, Object> initData = CoachState.initialState(sessionId, scenario, persona);
        var state = new CoachState(initData);
        activeStates.put(sessionId, state);
        tokenTracker.initSession(sessionId);
        log.info("SessionStateStore: initialized session {}", sessionId);
    }

    public boolean exists(String sessionId) {
        return activeStates.containsKey(sessionId);
    }

    public void remove(String sessionId) {
        activeStates.remove(sessionId);
        tokenTracker.removeSession(sessionId);
        log.info("SessionStateStore: removed session {}", sessionId);
    }

    CoachState getForExecution(String sessionId) {
        return activeStates.get(sessionId);
    }

    public void addMessage(String sessionId, MessageRole role, String content) {
        CoachState state = activeStates.get(sessionId);
        if (state != null) {
            synchronized (state) {
                state.messages().add(new MessageData(role, content));
            }
        }
    }

    public void addCorrections(String sessionId, List<CorrectionData> corrections) {
        CoachState state = activeStates.get(sessionId);
        if (state != null) {
            synchronized (state) {
                state.corrections().addAll(corrections);
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
