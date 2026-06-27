package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.ErrorType;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.service.LlmCallLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReportAgentTest {

    private ReportAgent agent;
    private StubChatModel chatModel;

    private static class StubChatModel implements ChatLanguageModel {
        List<ChatMessage> lastMessages;
        private Response<AiMessage> response;

        void setResponse(String text) {
            this.response = Response.from(
                    AiMessage.from(text),
                    new TokenUsage(10, 5)
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
        PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());
        LlmCallLogService logService = mock(LlmCallLogService.class);
        LlmReqConstructor llmReqConstructor = new LlmReqConstructor(chatModel, chatModel, logService);
        agent = new ReportAgent(llmReqConstructor, promptLoader, new ObjectMapper());
    }

    private String lastUserContent() {
        assertThat(chatModel.lastMessages).isNotNull();
        return ((UserMessage) chatModel.lastMessages.get(chatModel.lastMessages.size() - 1)).singleText();
    }

    private String lastSystemContent() {
        assertThat(chatModel.lastMessages).isNotNull();
        return ((SystemMessage) chatModel.lastMessages.get(0)).text();
    }

    @Test
    void validJsonResponseParsesAllFields() {
        chatModel.setResponse("""
                {
                  "overallAssessment": "Good progress",
                  "topicSummary": "Talked about work and travel",
                  "errorSummary": "Grammar: 2, Chinglish: 1",
                  "fluencyScore": 7,
                  "keyTakeaway": "Practice past tense"
                }""");

        ReportResult result = agent.generate(
                List.of(new MessageData(MessageRole.USER, "I go yesterday", 1)),
                List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP")
        );

        assertThat(result.overallAssessment()).isEqualTo("Good progress");
        assertThat(result.errorSummary()).isEqualTo("Grammar: 2, Chinglish: 1");
        assertThat(result.fluencyScore()).isEqualTo(7);
        assertThat(result.keyTakeaway()).isEqualTo("Practice past tense");
    }

    @Test
    void missingKeysDefaultToEmptyOrZero() {
        chatModel.setResponse("{}");
        ReportResult result = agent.generate(List.of(), List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result.overallAssessment()).isEmpty();
        assertThat(result.errorSummary()).isEmpty();
        assertThat(result.fluencyScore()).isZero();
        assertThat(result.keyTakeaway()).isEmpty();
    }

    @Test
    void invalidJsonFallsBackToRawResponse() {
        chatModel.setResponse("Some unstructured report text");
        ReportResult result = agent.generate(List.of(), List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result.overallAssessment()).isEqualTo("Some unstructured report text");
        assertThat(result.errorSummary()).isEmpty();
        assertThat(result.fluencyScore()).isZero();
    }

    @Test
    void conversationTextFiltersOutCorrectionMessages() {
        chatModel.setResponse("{}");

        agent.generate(
                List.of(
                        new MessageData(MessageRole.USER, "Hello", 1),
                        new MessageData(MessageRole.AGENT, "Hi there", 1),
                        new MessageData(MessageRole.CORRECTION, "corrected: hello", 1),
                        new MessageData(MessageRole.USER, "How are you", 2)
                ),
                List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP")
        );

        String userContent = lastUserContent();
        assertThat(userContent).contains("USER: Hello");
        assertThat(userContent).contains("AGENT: Hi there");
        assertThat(userContent).contains("USER: How are you");
        assertThat(userContent).doesNotContain("CORRECTION");
    }

    @Test
    void errorsTextWithCorrectionsIsPrettyPrinted() {
        chatModel.setResponse("{}");

        agent.generate(
                List.of(new MessageData(MessageRole.USER, "he go", 1)),
                List.of(new CorrectionData(ErrorType.GRAMMAR, "he go", "he goes", "s")),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP")
        );

        String userContent = lastUserContent();
        assertThat(userContent).contains("\"type\"");
        assertThat(userContent).contains("\"original\"");
        assertThat(userContent).contains("\"he go\"");
    }

    @Test
    void errorsTextEmptyCorrectionsShowsPlaceholder() {
        chatModel.setResponse("{}");

        agent.generate(
                List.of(new MessageData(MessageRole.USER, "test", 1)),
                List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP")
        );

        assertThat(lastUserContent()).contains("No errors recorded");
    }

    @Test
    void partialJsonUsesDefaultsForMissingFields() {
        chatModel.setResponse("{\"fluencyScore\":5}");

        ReportResult result = agent.generate(List.of(), List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result.fluencyScore()).isEqualTo(5);
        assertThat(result.overallAssessment()).isEmpty();
        assertThat(result.keyTakeaway()).isEmpty();
    }

    @Test
    void nestedObjectValuesAreSerializedToString() {
        chatModel.setResponse("{\"errorSummary\":{\"GRAMMAR\":3,\"CHINGLISH\":1},\"fluencyScore\":\"7\"}");

        ReportResult result = agent.generate(List.of(), List.of(),
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result.errorSummary()).contains("GRAMMAR");
        assertThat(result.errorSummary()).contains("CHINGLISH");
        assertThat(result.fluencyScore()).isEqualTo(7);
    }

    @Test
    void japaneseModeUsesJapaneseReportTemplate() {
        chatModel.setResponse("{\"overallAssessment\":\"良好\",\"fluencyScore\":6}");

        agent.generate(
                List.of(new MessageData(MessageRole.USER, "こんにちは", 1)),
                List.of(),
                new TaskContext("s1", "u1", "JAPANESE_BUSINESS")
        );

        // System message should be in Japanese (per-mode override)
        assertThat(lastSystemContent()).contains("日本語");
        assertThat(lastSystemContent()).doesNotContain("English coach");
    }
}
