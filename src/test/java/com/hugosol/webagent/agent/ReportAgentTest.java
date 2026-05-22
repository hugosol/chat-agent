package com.hugosol.webagent.agent;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.ErrorType;
import com.hugosol.webagent.model.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportAgentTest {

    private ReportAgent agent;
    private StubChatModel chatModel;

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
        PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());
        agent = new ReportAgent(chatModel, promptLoader, new ObjectMapper());
    }

    @Test
    void validJsonResponseParsesAllFields() {
        chatModel.setResponse("""
                {
                  "overallAssessment": "Good progress",
                  "topicSummary": "Talked about work and travel",
                  "errorSummary": "Grammar: 2, Chinglish: 1",
                  "vocabularySuggestions": "try however instead of but",
                  "fluencyScore": 7,
                  "keyTakeaway": "Practice past tense"
                }""");

        ReportResult result = agent.generate(
                List.of(new MessageData(MessageRole.USER, "I go yesterday", 1)),
                List.of()
        );

        assertThat(result.overallAssessment()).isEqualTo("Good progress");
        assertThat(result.topicSummary()).isEqualTo("Talked about work and travel");
        assertThat(result.errorSummary()).isEqualTo("Grammar: 2, Chinglish: 1");
        assertThat(result.vocabularySuggestions()).isEqualTo("try however instead of but");
        assertThat(result.fluencyScore()).isEqualTo(7);
        assertThat(result.keyTakeaway()).isEqualTo("Practice past tense");
    }

    @Test
    void missingKeysDefaultToEmptyOrZero() {
        chatModel.setResponse("{}");
        ReportResult result = agent.generate(List.of(), List.of());

        assertThat(result.overallAssessment()).isEmpty();
        assertThat(result.topicSummary()).isEmpty();
        assertThat(result.errorSummary()).isEmpty();
        assertThat(result.vocabularySuggestions()).isEmpty();
        assertThat(result.fluencyScore()).isZero();
        assertThat(result.keyTakeaway()).isEmpty();
    }

    @Test
    void invalidJsonFallsBackToRawResponse() {
        chatModel.setResponse("Some unstructured report text");
        ReportResult result = agent.generate(List.of(), List.of());

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
                List.of()
        );

        assertThat(chatModel.lastPrompt).contains("USER: Hello");
        assertThat(chatModel.lastPrompt).contains("AGENT: Hi there");
        assertThat(chatModel.lastPrompt).contains("USER: How are you");
        assertThat(chatModel.lastPrompt).doesNotContain("CORRECTION");
    }

    @Test
    void errorsTextWithCorrectionsIsPrettyPrinted() {
        chatModel.setResponse("{}");

        agent.generate(
                List.of(new MessageData(MessageRole.USER, "he go", 1)),
                List.of(new CorrectionData(ErrorType.GRAMMAR, "he go", "he goes", "s"))
        );

        assertThat(chatModel.lastPrompt).contains("\"type\"");
        assertThat(chatModel.lastPrompt).contains("\"original\"");
        assertThat(chatModel.lastPrompt).contains("\"he go\"");
    }

    @Test
    void errorsTextEmptyCorrectionsShowsPlaceholder() {
        chatModel.setResponse("{}");

        agent.generate(
                List.of(new MessageData(MessageRole.USER, "test", 1)),
                List.of()
        );

        assertThat(chatModel.lastPrompt).contains("No errors recorded");
    }

    @Test
    void partialJsonUsesDefaultsForMissingFields() {
        chatModel.setResponse("{\"fluencyScore\":5}");

        ReportResult result = agent.generate(List.of(), List.of());

        assertThat(result.fluencyScore()).isEqualTo(5);
        assertThat(result.overallAssessment()).isEmpty();
        assertThat(result.topicSummary()).isEmpty();
        assertThat(result.keyTakeaway()).isEmpty();
    }

    @Test
    void nestedObjectValuesAreSerializedToString() {
        chatModel.setResponse("{\"errorSummary\":{\"GRAMMAR\":3,\"CHINGLISH\":1},\"fluencyScore\":\"7\"}");

        ReportResult result = agent.generate(List.of(), List.of());

        assertThat(result.errorSummary()).contains("GRAMMAR");
        assertThat(result.errorSummary()).contains("CHINGLISH");
        assertThat(result.fluencyScore()).isEqualTo(7);
    }
}
