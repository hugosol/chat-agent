package com.hugosol.webagent.service;

import com.hugosol.webagent.model.AgentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TokenTracker {

    private final int conversationLimit;
    private final double warnRatio;
    private final ConcurrentMap<String, ConcurrentMap<AgentType, Integer>> sessionCounts = new ConcurrentHashMap<>();

    public TokenTracker(
            @Value("${app.token-limit:128000}") int conversationLimit,
            @Value("${app.token-limit-ratio:0.8}") double warnRatio) {
        this.conversationLimit = conversationLimit;
        this.warnRatio = warnRatio;
    }

    public void initSession(String sessionId) {
        sessionCounts.put(sessionId, new ConcurrentHashMap<>());
    }

    public void addTokens(String sessionId, AgentType agent, int tokens) {
        sessionCounts.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .merge(agent, tokens, Integer::sum);
    }

    public int getTokenCount(String sessionId, AgentType agent) {
        ConcurrentMap<AgentType, Integer> counts = sessionCounts.get(sessionId);
        if (counts == null) {
            return 0;
        }
        return counts.getOrDefault(agent, 0);
    }

    public int getTotalTokenCount(String sessionId) {
        ConcurrentMap<AgentType, Integer> counts = sessionCounts.get(sessionId);
        if (counts == null) {
            return 0;
        }
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double getUsageRatio(String sessionId, AgentType agent) {
        int tokens = getTokenCount(sessionId, agent);
        int limit = getAgentLimit(agent);
        if (limit <= 0) {
            return 0;
        }
        return (double) tokens / limit;
    }

    public boolean isWarning(String sessionId, AgentType agent) {
        return getUsageRatio(sessionId, agent) >= warnRatio;
    }

    public void removeSession(String sessionId) {
        sessionCounts.remove(sessionId);
    }

    private int getAgentLimit(AgentType agent) {
        if (agent == AgentType.CONVERSATION) {
            return conversationLimit;
        }
        return 0;
    }
}
