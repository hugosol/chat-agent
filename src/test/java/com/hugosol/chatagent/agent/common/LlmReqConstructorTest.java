package com.hugosol.chatagent.agent.common;

import com.hugosol.chatagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmReqConstructorTest {

    private LlmReqConstructor llmReqConstructor;
    private StubChatModel chatModel;
    private StubChatModel reportChatModel;
    private LlmCallLogService logService;

    private static class StubChatModel implements ChatLanguageModel {
        List<ChatMessage> lastMessages;
        private Response<AiMessage> response;

        void setResponse(String text, int inputTokens, int outputTokens) {
            this.response = Response.from(
                    AiMessage.from(text),
                    new TokenUsage(inputTokens, outputTokens)
            );
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            this.lastMessages = messages;
            return response;
        }

        @Override
        public String chat(String userMessage) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        chatModel = new StubChatModel();
        reportChatModel = new StubChatModel();
        logService = mock(LlmCallLogService.class);
        llmReqConstructor = new LlmReqConstructor(chatModel, reportChatModel, logService);
    }

    @Test
    void successfulExecution_returnsParsedResult() {
        chatModel.setResponse("parsed output", 100, 50);
        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("You are a coach")
                .userTemplate("Hello {name}")
                .paramBuilder(p -> Map.of("name", p))
                .parser(r -> "[" + r + "]")
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s1", "u1", "WORKPLACE_STANDUP");
        String result = llmReqConstructor.execute(TaskName.CORRECTION, "World", ctx);

        assertThat(result).isEqualTo("[parsed output]");

        // Verify message assembly order: SystemMessage → UserMessage
        assertThat(chatModel.lastMessages).hasSize(2);
        assertThat(chatModel.lastMessages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) chatModel.lastMessages.get(0)).text()).isEqualTo("You are a coach");
        assertThat(chatModel.lastMessages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) chatModel.lastMessages.get(1)).singleText()).isEqualTo("Hello World");

        // Verify logging: systemPrompt + chatHistory + tokenUsage all populated
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).saveAsync(
                eq("s1"), eq("u1"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                isNull(), // requestPrompt unused now
                eq("You are a coach"), // systemPrompt
                contains("\"Hello World\""), // chatHistory
                eq("parsed output"),
                eq(100), // inputTokens
                eq(50), // outputTokens
                durationCaptor.capture(),
                eq("SUCCESS"), isNull()
        );
        assertThat(durationCaptor.getValue()).isNotNegative();
    }

    @Test
    void messageAssembly_includesExampleMessages() {
        chatModel.setResponse("result", 50, 10);
        List<ChatMessage> examples = List.of(
                UserMessage.from("Example input"),
                AiMessage.from("Example output")
        );
        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("System")
                .userTemplate("User {x}")
                .exampleMessages(examples)
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        llmReqConstructor.execute(TaskName.CORRECTION, "data", new TaskContext("s", "u", null));

        // Order: SystemMessage → example UserMessage → example AiMessage → real UserMessage
        assertThat(chatModel.lastMessages).hasSize(4);
        assertThat(chatModel.lastMessages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(chatModel.lastMessages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) chatModel.lastMessages.get(1)).singleText()).isEqualTo("Example input");
        assertThat(chatModel.lastMessages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) chatModel.lastMessages.get(2)).text()).isEqualTo("Example output");
        assertThat(chatModel.lastMessages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) chatModel.lastMessages.get(3)).singleText()).isEqualTo("User data");
    }

    @Test
    void parseFailure_withSwallowReturnsNull() {
        chatModel.setResponse("garbage response", 10, 5);
        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("{input}")
                .paramBuilder(p -> Map.of("input", p))
                .parser(r -> { throw new RuntimeException("can't parse"); })
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s1", "u1", "WORKPLACE_STANDUP");
        String result = llmReqConstructor.execute(TaskName.CORRECTION, "test", ctx);

        assertThat(result).isNull();

        verify(logService).saveAsync(
                eq("s1"), eq("u1"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                isNull(), eq("S"), anyString(),
                eq("garbage response"),
                eq(10), eq(5),
                anyLong(),
                eq("SUCCESS"), isNull()
        );
    }

    @Test
    void parseFailure_withThrowPropagatesException() {
        reportChatModel.setResponse("bad json", 5, 2);
        llmReqConstructor.register(TaskName.REPORT, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("{x}")
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> { throw new RuntimeException("parse error"); })
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        TaskContext ctx = new TaskContext("s2", "u2", "DAILY_TALK");
        assertThatThrownBy(() -> llmReqConstructor.execute(TaskName.REPORT, "input", ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("parse error");
    }

    @Test
    void llmApiFailure_withSwallowReturnsNull() {
        StubFailingModel failingModel = new StubFailingModel(new RuntimeException("API timeout"));
        llmReqConstructor = new LlmReqConstructor(failingModel, reportChatModel, logService);
        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("U")
                .paramBuilder(p -> Map.of())
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        TaskContext ctx = new TaskContext("s3", "u3", "WORKPLACE_STANDUP");
        String result = llmReqConstructor.execute(TaskName.CORRECTION, "whatever", ctx);

        assertThat(result).isNull();

        verify(logService).saveAsync(
                eq("s3"), eq("u3"), eq("CORRECTION"), eq("WORKPLACE_STANDUP"),
                isNull(), eq("S"), anyString(),
                isNull(),
                isNull(), isNull(),
                anyLong(),
                eq("ERROR"), eq("API timeout")
        );
    }

    @Test
    void unregisteredTask_throwsIllegalStateException() {
        TaskContext ctx = new TaskContext("s4", "u4", null);
        assertThatThrownBy(() -> llmReqConstructor.execute(TaskName.CORRECTION, "input", ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No task registered");
    }

    @Test
    void reportTaskUsesReportModel() {
        var defaultModel = new TrackingModel();
        var reportModel = new TrackingModel();
        llmReqConstructor = new LlmReqConstructor(defaultModel, reportModel, logService);

        llmReqConstructor.register(TaskName.REPORT, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("{x}")
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("{x}")
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        TaskContext ctx = new TaskContext("s5", "u5", "WORKPLACE_STANDUP");
        llmReqConstructor.execute(TaskName.REPORT, "input", ctx);

        assertThat(reportModel.wasCalled).isTrue();
        assertThat(defaultModel.wasCalled).isFalse();

        defaultModel.wasCalled = false;
        reportModel.wasCalled = false;

        llmReqConstructor.execute(TaskName.CORRECTION, "input", ctx);

        assertThat(defaultModel.wasCalled).isTrue();
        assertThat(reportModel.wasCalled).isFalse();
    }

    @Test
    void executeRaw_returnsRawResponseWithoutParsing() {
        chatModel.setResponse("raw string output", 20, 10);
        llmReqConstructor.register(TaskName.EXTRACT_TOPICS, LlmTaskDefinition
                .<String, String>builder()
                .systemTemplate("S")
                .userTemplate("{x}")
                .paramBuilder(p -> Map.of("x", p))
                .parser(r -> r)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        String result = llmReqConstructor.executeRaw(TaskName.EXTRACT_TOPICS, "input",
                new TaskContext("s", "u", null));

        assertThat(result).isEqualTo("raw string output");
    }

    private static class TrackingModel implements ChatLanguageModel {
        boolean wasCalled = false;

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            wasCalled = true;
            return Response.from(AiMessage.from("response"));
        }

        @Override
        public String chat(String userMessage) {
            return null;
        }
    }

    private static class StubFailingModel implements ChatLanguageModel {
        private final RuntimeException error;

        StubFailingModel(RuntimeException error) {
            this.error = error;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            throw error;
        }

        @Override
        public String chat(String userMessage) {
            return null;
        }
    }
}
