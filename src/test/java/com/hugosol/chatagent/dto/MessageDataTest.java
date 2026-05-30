package com.hugosol.chatagent.dto;

import com.hugosol.chatagent.model.MessageRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDataTest {

    @Test
    void constructorSetsFields() {
        MessageData msg = new MessageData(MessageRole.USER, "Hello", 1);

        assertThat(msg.getRole()).isEqualTo(MessageRole.USER);
        assertThat(msg.getContent()).isEqualTo("Hello");
        assertThat(msg.getMessageId()).isEqualTo(1);
    }

    @Test
    void constructorSetsTimestampNearNow() {
        long before = System.currentTimeMillis();
        MessageData msg = new MessageData(MessageRole.AGENT, "Hi", 2);
        long after = System.currentTimeMillis();

        assertThat(msg.getTimestamp()).isBetween(before, after);
    }

    @Test
    void defaultConstructorAllowsSetters() {
        MessageData msg = new MessageData();

        msg.setMessageId(5);
        msg.setRole(MessageRole.CORRECTION);
        msg.setContent("Fixed text");
        msg.setTimestamp(123456789L);

        assertThat(msg.getMessageId()).isEqualTo(5);
        assertThat(msg.getRole()).isEqualTo(MessageRole.CORRECTION);
        assertThat(msg.getContent()).isEqualTo("Fixed text");
        assertThat(msg.getTimestamp()).isEqualTo(123456789L);
    }

    @Test
    void allRoleValuesWork() {
        for (MessageRole role : MessageRole.values()) {
            MessageData msg = new MessageData(role, "test", 0);
            assertThat(msg.getRole()).isEqualTo(role);
        }
    }
}
