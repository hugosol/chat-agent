package com.hugosol.chatagent.graph;

import com.hugosol.chatagent.dto.MemoryCueQueue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStateTest {

    @Test
    void newChannelsHaveDefaults() {
        Map<String, Object> initial = ChatState.initialState("s1", "WORKPLACE_STANDUP", "u1", "", null);
        ChatState state = new ChatState(initial);

        assertThat(state.learningProfile()).isEqualTo("");
        assertThat(state.memoryCueQueue()).isNull();
    }

    @Test
    void learningProfileStoresValue() {
        Map<String, Object> initial = ChatState.initialState("s1", "WORKPLACE_STANDUP", "u1",
                "Past tense needs work", null);
        ChatState state = new ChatState(initial);

        assertThat(state.learningProfile()).isEqualTo("Past tense needs work");
    }

    @Test
    void nullValuesDefaultToEmpty() {
        Map<String, Object> initial = ChatState.initialState("s1", "WORKPLACE_STANDUP", "u1", null, null);
        ChatState state = new ChatState(initial);

        assertThat(state.learningProfile()).isEqualTo("");
    }

    @Test
    void existingChannelsStillWork() {
        Map<String, Object> initial = ChatState.initialState("s1", "WORKPLACE_STANDUP", "u1",
                "profile", null);
        ChatState state = new ChatState(initial);

        assertThat(state.sessionId()).isEqualTo("s1");
        assertThat(state.mode()).isEqualTo("WORKPLACE_STANDUP");
        assertThat(state.userId()).isEqualTo("u1");
        assertThat(state.stateStatus()).isEqualTo("IDLE");
    }
}
