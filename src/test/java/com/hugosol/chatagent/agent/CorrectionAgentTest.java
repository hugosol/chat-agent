package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.model.ErrorType;
import com.hugosol.chatagent.service.LlmCallLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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

class CorrectionAgentTest {

    private CorrectionAgent agent;
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
        agent = new CorrectionAgent(llmReqConstructor, promptLoader, new ObjectMapper());
    }

    @Test
    void nullInputReturnsEmptyList() {
        List<CorrectionData> result = agent.analyze(null, new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));
        assertThat(result).isEmpty();
    }

    @Test
    void blankInputReturnsEmptyList() {
        List<CorrectionData> result = agent.analyze("   ", new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));
        assertThat(result).isEmpty();
    }

    @Test
    void validJsonArrayReturnsCorrections() {
        chatModel.setResponse("""
                [
                  {"type": "GRAMMAR", "original": "he go", "corrected": "he goes", "explanation": "s"},\s
                  {"type": "CHINGLISH", "original": "open the light", "corrected": "turn on the light", "explanation": "c"}
                ]""");

        List<CorrectionData> result = agent.analyze("he go to open the light",
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo(ErrorType.GRAMMAR);
        assertThat(result.get(0).getOriginal()).isEqualTo("he go");
        assertThat(result.get(1).getType()).isEqualTo(ErrorType.CHINGLISH);
        assertThat(result.get(1).getOriginal()).isEqualTo("open the light");
    }

    @Test
    void jsonWrappedInSurroundingTextIsExtracted() {
        chatModel.setResponse("Here are the corrections:\n" +
                "[{\"type\":\"GRAMMAR\",\"original\":\"x\",\"corrected\":\"y\",\"explanation\":\"z\"}]\n" +
                "Hope that helps!");

        List<CorrectionData> result = agent.analyze("test input",
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(ErrorType.GRAMMAR);
    }

    @Test
    void noBracketsReturnsEmptyList() {
        chatModel.setResponse("No corrections needed today.");
        List<CorrectionData> result = agent.analyze("test",
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));
        assertThat(result).isEmpty();
    }

    @Test
    void invalidJsonReturnsEmptyList() {
        chatModel.setResponse("[{\"type\":\"GRAMMAR\", broken json");
        List<CorrectionData> result = agent.analyze("test",
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));
        assertThat(result).isEmpty();
    }

    @Test
    void emptyJsonArrayReturnsEmptyList() {
        chatModel.setResponse("[]");
        List<CorrectionData> result = agent.analyze("test",
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));
        assertThat(result).isEmpty();
    }

    @Test
    void userInputIsSubstitutedIntoUserMessage() {
        chatModel.setResponse("[]");
        agent.analyze("I go to school", new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        // The last message should be the user message with substituted input
        assertThat(chatModel.lastMessages).isNotNull();
        UserMessage userMsg = (UserMessage) chatModel.lastMessages.get(chatModel.lastMessages.size() - 1);
        assertThat(userMsg.singleText()).contains("I go to school");
    }
}
