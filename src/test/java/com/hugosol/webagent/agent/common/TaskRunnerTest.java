package com.hugosol.webagent.agent.common;

import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskRunnerTest {

    private TaskRunner runner;
    private ChatLanguageModel chatModel;
    private LlmCallLogService logService;

    private static class StubChatModel implements ChatLanguageModel {
        String lastPrompt;
        private String response;

        void setResponse(String response) {
            this.response = response;
        }

        @Override
        public String chat(String prompt) {
            this.lastPrompt = prompt;
            return response;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        chatModel = new StubChatModel();
        logService = mock(LlmCallLogService.class);
        runner = new TaskRunner(chatModel, logService);
    }

    @Test
    void successfulExecution_returnsParsedResult() {
        ((StubChatModel) chatModel).setResponse("parsed output");
        runner.register(TaskName.CORRECTION, TaskDefinition
                .<String, String>builder()
                .template("Hello {name}")
                .paramBuilder(p -> Map.of("name", p))
                .parser(r -> "[" + r + "]")
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s1", "u1", "WORKPLACE_STANDUP");
        String result = runner.execute(TaskName.CORRECTION, "World", ctx);

        assertThat(result).isEqualTo("[parsed output]");
        assertThat(((StubChatModel) chatModel).lastPrompt).isEqualTo("Hello World");

        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).saveAsync(
                eq("s1"), eq("u1"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                eq("Hello World"), isNull(), isNull(),
                eq("parsed output"),
                isNull(), isNull(),
                durationCaptor.capture(),
                eq("SUCCESS"), isNull()
        );
        assertThat(durationCaptor.getValue()).isNotNegative();
    }

    @Test
    void parseFailure_withSwallowReturnsNull() {
        ((StubChatModel) chatModel).setResponse("garbage response");
        runner.register(TaskName.CORRECTION, TaskDefinition
                .<String, String>builder()
                .template("prompt: {input}")
                .paramBuilder(p -> Map.of("input", p))
                .parser(r -> { throw new RuntimeException("can't parse"); })
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s1", "u1", "WORKPLACE_STANDUP");
        String result = runner.execute(TaskName.CORRECTION, "test", ctx);

        assertThat(result).isNull();

        verify(logService).saveAsync(
                eq("s1"), eq("u1"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                eq("prompt: test"), isNull(), isNull(),
                eq("garbage response"),
                isNull(), isNull(),
                anyLong(),
                eq("SUCCESS"), isNull()
        );
    }

    @Test
    void parseFailure_withThrowPropagatesException() {
        ((StubChatModel) chatModel).setResponse("bad json");
        runner.register(TaskName.REPORT, TaskDefinition
                .<String, String>builder()
                .template("{x}")
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> { throw new RuntimeException("parse error"); })
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        TaskContext ctx = new TaskContext("s2", "u2", "DAILY_TALK");
        assertThatThrownBy(() -> runner.execute(TaskName.REPORT, "input", ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("parse error");
    }

    @Test
    void llmApiFailure_withSwallowReturnsNull() {
        ((StubChatModel) chatModel).setResponse(null);
        StubFailingModel failingModel = new StubFailingModel(new RuntimeException("API timeout"));
        runner = new TaskRunner(failingModel, logService);
        runner.register(TaskName.CORRECTION, TaskDefinition
                .<String, String>builder()
                .template("test")
                .paramBuilder(p -> Map.of())
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s3", "u3", "WORKPLACE_STANDUP");
        String result = runner.execute(TaskName.CORRECTION, "whatever", ctx);

        assertThat(result).isNull();

        verify(logService).saveAsync(
                eq("s3"), eq("u3"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                eq("test"), isNull(), isNull(),
                isNull(),
                isNull(), isNull(),
                anyLong(),
                eq("ERROR"), eq("API timeout")
        );
    }

    @Test
    void unregisteredTask_throwsIllegalStateException() {
        TaskContext ctx = new TaskContext("s4", "u4", null);
        assertThatThrownBy(() -> runner.execute(TaskName.CORRECTION, "input", ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No task registered");
    }

    private static class StubFailingModel implements ChatLanguageModel {
        private final RuntimeException error;

        StubFailingModel(RuntimeException error) {
            this.error = error;
        }

        @Override
        public String chat(String prompt) {
            throw error;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return null;
        }
    }
}
