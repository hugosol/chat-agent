package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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
        LlmCallLogService logService = mock(LlmCallLogService.class);
        TaskRunner runner = new TaskRunner(chatModel, logService);
        agent = new LearningAgent(runner, promptLoader, new AppProperties());
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

        assertThat(chatModel.lastPrompt).contains("Old: past tense weak");
        assertThat(chatModel.lastPrompt).contains("New: 3 grammar errors");
    }

    @Test
    void mergeProfile_handlesEmptyOldProfile() {
        chatModel.setResponse("fresh profile");

        agent.mergeProfile("", "error data",
                new TaskContext(null, "u1", null));

        assertThat(chatModel.lastPrompt).doesNotContain("null");
        assertThat(chatModel.lastPrompt).contains("error data");
    }
}
