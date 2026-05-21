package com.hugosol.webagent.service;

import com.hugosol.webagent.model.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTrackerTest {

    private static final int LIMIT = 128000;
    private static final double WARN_RATIO = 0.8;

    private TokenTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TokenTracker(LIMIT, WARN_RATIO);
    }

    @Test
    void uninitializedSessionReturnsZero() {
        assertThat(tracker.getTokenCount("unknown", AgentType.CONVERSATION)).isZero();
        assertThat(tracker.getTotalTokenCount("unknown")).isZero();
    }

    @Test
    void initSessionSetsUpEmptyTracking() {
        tracker.initSession("s1");
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isZero();
    }

    @Test
    void addTokensAccumulates() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, 500);
        tracker.addTokens("s1", AgentType.CONVERSATION, 300);
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isEqualTo(800);
    }

    @Test
    void addTokensWithoutInitAutoCreatesSession() {
        tracker.addTokens("s1", AgentType.CONVERSATION, 100);
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isEqualTo(100);
    }

    @Test
    void multipleAgentsTrackedIndependently() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, 500);
        tracker.addTokens("s1", AgentType.CORRECTION, 200);
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isEqualTo(500);
        assertThat(tracker.getTokenCount("s1", AgentType.CORRECTION)).isEqualTo(200);
    }

    @Test
    void getTotalTokenCountSumsAllAgents() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, 500);
        tracker.addTokens("s1", AgentType.CORRECTION, 200);
        tracker.addTokens("s1", AgentType.REPORT, 100);
        assertThat(tracker.getTotalTokenCount("s1")).isEqualTo(800);
    }

    @Test
    void getUsageRatioForConversation() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, 64000); // 50%
        assertThat(tracker.getUsageRatio("s1", AgentType.CONVERSATION)).isEqualTo(0.5);
    }

    @Test
    void getUsageRatioForNonConversationReturnsZero() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CORRECTION, 500);
        assertThat(tracker.getUsageRatio("s1", AgentType.CORRECTION)).isZero();
    }

    @Test
    void getUsageRatioForEmptySessionReturnsZero() {
        assertThat(tracker.getUsageRatio("unknown", AgentType.CONVERSATION)).isZero();
    }

    @Test
    void isWarningBelowRatioReturnsFalse() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, (int) (LIMIT * 0.79));
        assertThat(tracker.isWarning("s1", AgentType.CONVERSATION)).isFalse();
    }

    @Test
    void isWarningAtRatioReturnsTrue() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, (int) (LIMIT * WARN_RATIO));
        assertThat(tracker.isWarning("s1", AgentType.CONVERSATION)).isTrue();
    }

    @Test
    void isWarningAboveRatioReturnsTrue() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, (int) (LIMIT * 0.9));
        assertThat(tracker.isWarning("s1", AgentType.CONVERSATION)).isTrue();
    }

    @Test
    void removeSessionClearsData() {
        tracker.initSession("s1");
        tracker.addTokens("s1", AgentType.CONVERSATION, 500);
        tracker.removeSession("s1");
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isZero();
        assertThat(tracker.getTotalTokenCount("s1")).isZero();
    }

    @Test
    void removeNonExistentSessionDoesNotThrow() {
        tracker.removeSession("never-existed");
    }

    @Test
    void sessionsAreIsolated() {
        tracker.initSession("s1");
        tracker.initSession("s2");
        tracker.addTokens("s1", AgentType.CONVERSATION, 100);
        tracker.addTokens("s2", AgentType.CONVERSATION, 200);
        assertThat(tracker.getTokenCount("s1", AgentType.CONVERSATION)).isEqualTo(100);
        assertThat(tracker.getTokenCount("s2", AgentType.CONVERSATION)).isEqualTo(200);
    }
}
