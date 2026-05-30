package com.hugosol.chatagent.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientMessage.StartSession.class, name = "START_SESSION"),
        @JsonSubTypes.Type(value = ClientMessage.UserInput.class, name = "USER_INPUT"),
        @JsonSubTypes.Type(value = ClientMessage.EndSession.class, name = "END_SESSION"),
        @JsonSubTypes.Type(value = ClientMessage.ResumeSession.class, name = "RESUME_SESSION"),
        @JsonSubTypes.Type(value = ClientMessage.LoadHistory.class, name = "LOAD_HISTORY")
})
public sealed interface ClientMessage
        permits ClientMessage.StartSession, ClientMessage.UserInput, ClientMessage.EndSession,
                ClientMessage.ResumeSession, ClientMessage.LoadHistory {

    record StartSession(String mode) implements ClientMessage {
    }

    record UserInput(String text, int messageId) implements ClientMessage {
    }

    record EndSession() implements ClientMessage {
    }

    record ResumeSession(String sessionId) implements ClientMessage {
    }

    record LoadHistory() implements ClientMessage {
    }
}
