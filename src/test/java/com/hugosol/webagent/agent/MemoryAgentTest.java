package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAgentTest {

    private MemoryAgent agent;
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
        agent = new MemoryAgent(chatModel, promptLoader);
    }

    @Test
    void mergeTopic_returnsTrimmedResponse() {
        chatModel.setResponse("  Discussed login module and session management.  ");

        String result = agent.mergeTopic("old topic", "new summary");

        assertThat(result).isEqualTo("Discussed login module and session management.");
    }

    @Test
    void mergeTopic_includesOldAndNewDataInPrompt() {
        chatModel.setResponse("merged");

        agent.mergeTopic("Talked about travel plans", "Today discussed presentation prep");

        assertThat(chatModel.lastPrompt).contains("Talked about travel plans");
        assertThat(chatModel.lastPrompt).contains("Today discussed presentation prep");
    }

    @Test
    void mergeTopic_handlesEmptyOldSummary() {
        chatModel.setResponse("fresh summary");

        agent.mergeTopic("", "new session data");

        assertThat(chatModel.lastPrompt).doesNotContain("null");
        assertThat(chatModel.lastPrompt).contains("new session data");
    }

    @Test
    void mergeProfile_returnsTrimmedResponse() {
        chatModel.setResponse("  Past tense errors (40%), article usage improving.  ");

        String result = agent.mergeProfile("old profile", "error data", "vocab suggestions");

        assertThat(result).isEqualTo("Past tense errors (40%), article usage improving.");
    }

    @Test
    void mergeProfile_includesAllInputsInPrompt() {
        chatModel.setResponse("merged profile");

        agent.mergeProfile("Old: past tense weak", "New: 3 grammar errors", "Try: 'staff' instead of 'people'");

        assertThat(chatModel.lastPrompt).contains("Old: past tense weak");
        assertThat(chatModel.lastPrompt).contains("New: 3 grammar errors");
        assertThat(chatModel.lastPrompt).contains("Try: 'staff' instead of 'people'");
    }

    @Test
    void mergeProfile_handlesEmptyOldProfile() {
        chatModel.setResponse("fresh profile");

        agent.mergeProfile("", "error data", "vocab suggestions");

        assertThat(chatModel.lastPrompt).doesNotContain("null");
        assertThat(chatModel.lastPrompt).contains("error data");
    }
}
