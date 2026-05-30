package com.hugosol.chatagent.service;

import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.dto.MemoryCueQueue;
import com.hugosol.chatagent.graph.ChatState;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.AgentType;
import com.hugosol.chatagent.model.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Map<String, ChatState> activeStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToWs = new ConcurrentHashMap<>();
    private final Map<String, List<CompletableFuture<Void>>> pendingCorrections = new ConcurrentHashMap<>();
    private final TokenTracker tokenTracker;
    private final LearningProfileService learningProfileService;
    private final AppProperties appProperties;

    public SessionService(TokenTracker tokenTracker, LearningProfileService learningProfileService, AppProperties appProperties) {
        this.tokenTracker = tokenTracker;
        this.learningProfileService = learningProfileService;
        this.appProperties = appProperties;
    }

    public void init(String sessionId, String mode, String userId, String wsId) {
        String learningProfile = learningProfileService.loadLatestContent(userId, "LEARNING_PROFILE", null);
        int queueCapacity = appProperties.getMemory().getRetrieval().getTopK() + 1;
        var memoryCueQueue = new MemoryCueQueue(queueCapacity);
        Map<String, Object> initData = ChatState.initialState(sessionId, mode, userId, learningProfile, memoryCueQueue);
        var state = new ChatState(initData);
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
        ChatState state = activeStates.get(sessionId);
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
        ChatState state = activeStates.get(sessionId);
        if (state != null) {
            synchronized (state) {
                MessageData md = new MessageData(role, content, messageId);
                md.setTokenCount(tokenCount);
                state.addMessage(md);
            }
        }
    }

    public void addCorrections(String sessionId, List<CorrectionData> corrections) {
        ChatState state = activeStates.get(sessionId);
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
        ChatState state = activeStates.get(sessionId);
        return state != null ? new ArrayList<>(state.messages()) : List.of();
    }

    public List<CorrectionData> getCorrections(String sessionId) {
        ChatState state = activeStates.get(sessionId);
        return state != null ? new ArrayList<>(state.corrections()) : List.of();
    }

    public String getMode(String sessionId) {
        ChatState state = activeStates.get(sessionId);
        return state != null ? state.mode() : "";
    }

    public int getCorrectionCount(String sessionId) {
        ChatState state = activeStates.get(sessionId);
        return state != null ? state.corrections().size() : 0;
    }

    public String getLearningProfile(String sessionId) {
        ChatState state = activeStates.get(sessionId);
        return state != null ? state.learningProfile() : "";
    }

    public MemoryCueQueue getMemoryCueQueue(String sessionId) {
        ChatState state = activeStates.get(sessionId);
        return state != null ? state.memoryCueQueue() : null;
    }

    public double getUsageRatio(String sessionId) {
        return tokenTracker.getUsageRatio(sessionId, AgentType.CONVERSATION);
    }

    public boolean isTokenWarning(String sessionId) {
        return tokenTracker.isWarning(sessionId, AgentType.CONVERSATION);
    }

    public void addPendingCorrection(String sessionId, CompletableFuture<Void> future) {
        pendingCorrections.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(future);
    }

    public void waitForPendingCorrections(String sessionId, long timeoutMs) {
        List<CompletableFuture<Void>> futures = pendingCorrections.remove(sessionId);
        if (futures == null || futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timed out waiting for {} pending corrections for session {}",
                    futures.size(), sessionId);
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.warn("Error waiting for pending corrections for session {}: {}", sessionId, e.getMessage());
        }
    }
}
