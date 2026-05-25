package com.hugosol.webagent.config;

import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoggableChatModelTest {

    private LlmCallLogService logService;
    private LoggableChatModel loggable;

    private static class StubDelegate implements ChatLanguageModel {
        String prompt;
        private String response;
        private RuntimeException error;

        void setResponse(String response) { this.response = response; }
        void setError(RuntimeException error) { this.error = error; }

        @Override
        public String chat(String prompt) {
            this.prompt = prompt;
            if (error != null) throw error;
            return response;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return null;
        }
    }

    private StubDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new StubDelegate();
        logService = mock(LlmCallLogService.class);
        loggable = new LoggableChatModel(delegate, logService);
    }

    @Test
    void successfulCall_logsSuccessRecord() {
        delegate.setResponse("Corrected text");
        String result = loggable.chat("Fix this grammar");

        assertThat(result).isEqualTo("Corrected text");
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).saveAsync(
                isNull(), isNull(), isNull(), isNull(),
                eq("{\"text\":\"Fix this grammar\"}"),
                eq("Corrected text"),
                isNull(), isNull(),
                durationCaptor.capture(),
                eq("SUCCESS"),
                isNull()
        );
        assertThat(durationCaptor.getValue()).isNotNegative();
    }

    @Test
    void failedCall_logsErrorRecord() {
        delegate.setError(new RuntimeException("API timeout"));

        assertThatThrownBy(() -> loggable.chat("Bad prompt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("API timeout");

        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).saveAsync(
                isNull(), isNull(), isNull(), isNull(),
                eq("{\"text\":\"Bad prompt\"}"),
                isNull(),
                isNull(), isNull(),
                durationCaptor.capture(),
                eq("ERROR"),
                eq("API timeout")
        );
        assertThat(durationCaptor.getValue()).isNotNegative();
    }

    @Test
    void chatResponseIsNotAffectedByLogging() {
        delegate.setResponse("Some LLM output");
        String result = loggable.chat("Hello");

        assertThat(result).isEqualTo("Some LLM output");
        assertThat(delegate.prompt).isEqualTo("Hello");
    }
}
