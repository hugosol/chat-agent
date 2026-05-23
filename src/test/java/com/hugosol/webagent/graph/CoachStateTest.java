package com.hugosol.webagent.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoachStateTest {

    @Test
    void newChannelsHaveDefaults() {
        Map<String, Object> initial = CoachState.initialState("s1", "WORKPLACE_STANDUP", "u1", "", "");
        CoachState state = new CoachState(initial);

        assertThat(state.topicMemory()).isEqualTo("");
        assertThat(state.learningProfile()).isEqualTo("");
    }

    @Test
    void topicMemoryStoresValue() {
        Map<String, Object> initial = CoachState.initialState("s1", "WORKPLACE_STANDUP", "u1",
                "Discussed travel plans", "Past tense needs work");
        CoachState state = new CoachState(initial);

        assertThat(state.topicMemory()).isEqualTo("Discussed travel plans");
        assertThat(state.learningProfile()).isEqualTo("Past tense needs work");
    }

    @Test
    void nullValuesDefaultToEmpty() {
        Map<String, Object> initial = CoachState.initialState("s1", "WORKPLACE_STANDUP", "u1", null, null);
        CoachState state = new CoachState(initial);

        assertThat(state.topicMemory()).isEqualTo("");
        assertThat(state.learningProfile()).isEqualTo("");
    }

    @Test
    void existingChannelsStillWork() {
        Map<String, Object> initial = CoachState.initialState("s1", "WORKPLACE_STANDUP", "u1",
                "topic", "profile");
        CoachState state = new CoachState(initial);

        assertThat(state.sessionId()).isEqualTo("s1");
        assertThat(state.mode()).isEqualTo("WORKPLACE_STANDUP");
        assertThat(state.userId()).isEqualTo("u1");
        assertThat(state.stateStatus()).isEqualTo("IDLE");
    }
}
