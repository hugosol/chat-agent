package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.service.LlmCallLogService;
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

class LearningAgentTest {

    private LearningAgent agent;
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
        agent = new LearningAgent(llmReqConstructor, promptLoader, new AppProperties());
    }

    private String lastUserContent() {
        assertThat(chatModel.lastMessages).isNotNull();
        return ((UserMessage) chatModel.lastMessages.get(chatModel.lastMessages.size() - 1)).singleText();
    }

    @Test
    void mergeProfile_returnsTrimmedResponse() {
        chatModel.setResponse("  Past tense errors (40%), article usage improving.  ");

        String result = agent.mergeProfile("old profile", "error data",
                new TaskContext("s1", "u1", null));

        assertThat(result).isEqualTo("Past tense errors (40%), article usage improving.");
    }

    @Test
    void mergeProfile_includesAllInputsInPrompt() {
        chatModel.setResponse("merged profile");

        agent.mergeProfile("Old: past tense weak", "New: 3 grammar errors",
                new TaskContext("s1", "u1", null));

        String userContent = lastUserContent();
        assertThat(userContent).contains("Old: past tense weak");
        assertThat(userContent).contains("New: 3 grammar errors");
    }

    @Test
    void mergeProfile_handlesEmptyOldProfile() {
        chatModel.setResponse("fresh profile");

        agent.mergeProfile("", "error data",
                new TaskContext(null, "u1", null));

        String userContent = lastUserContent();
        assertThat(userContent).doesNotContain("null");
        assertThat(userContent).contains("error data");
        // Empty oldProfile should be replaced with placeholder
        assertThat(userContent).contains("(No previous sessions)");
    }
}
