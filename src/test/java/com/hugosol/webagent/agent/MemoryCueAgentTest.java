package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryCueAgentTest {

    private MemoryCueAgent agent;
    private StubChatModel chatModel;

    private static class StubChatModel implements ChatLanguageModel {
        String lastPrompt;
        String response;

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
        agent = new MemoryCueAgent(chatModel, promptLoader, new AppProperties());
    }

    @Test
    void detectSwitches_returnsEmptyListWhenNoSwitch() {
        chatModel.setResponse("[]");

        List<Integer> result = agent.detectSwitches(List.of(
                msg(0, "Hello"), msg(1, "Hi there")
        ), AgentMode.WORKPLACE_STANDUP);

        assertThat(result).isEmpty();
    }

    @Test
    void detectSwitches_returnsSingleSwitchIndex() {
        chatModel.setResponse("[3]");

        List<Integer> result = agent.detectSwitches(buildMessages(6), AgentMode.DAILY_TALK);

        assertThat(result).containsExactly(3);
    }

    @Test
    void detectSwitches_returnsMultipleSwitchIndexes() {
        chatModel.setResponse("[2, 5]");

        List<Integer> result = agent.detectSwitches(buildMessages(8), AgentMode.WORKPLACE_STANDUP);

        assertThat(result).containsExactly(2, 5);
    }

    @Test
    void detectSwitches_handlesExtraTextBeforeJson() {
        chatModel.setResponse("The topic switches at these points: [3]");

        List<Integer> result = agent.detectSwitches(buildMessages(6), AgentMode.WORKPLACE_STANDUP);

        assertThat(result).containsExactly(3);
    }

    @Test
    void generateCue_returnsTopicSummary() {
        chatModel.setResponse("{\"topic\": \"Travel\", \"summary\": \"Talked about Japan trip\"}");

        var result = agent.generateCue(
                List.of(msg(0, "I went to Japan"), msg(1, "That sounds fun")),
                AgentMode.WORKPLACE_STANDUP, 0);

        assertThat(result.topic()).isEqualTo("Travel");
        assertThat(result.summary()).isEqualTo("Talked about Japan trip");
    }

    @Test
    void generateCue_throwsOnInvalidJson() {
        chatModel.setResponse("not valid json");

        assertThatThrownBy(() -> agent.generateCue(
                List.of(msg(0, "Hi")), AgentMode.DAILY_TALK, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void detectSwitches_injectsMessageIndexesInPrompt() {
        chatModel.setResponse("[]");

        agent.detectSwitches(buildMessages(4), AgentMode.WORKPLACE_STANDUP);

        assertThat(chatModel.lastPrompt).contains("[MSG#0]");
        assertThat(chatModel.lastPrompt).contains("[MSG#1]");
        assertThat(chatModel.lastPrompt).contains("[MSG#2]");
        assertThat(chatModel.lastPrompt).contains("[MSG#3]");
    }

    private static MessageData msg(int id, String text) {
        return new MessageData(id % 2 == 0 ? MessageRole.USER : MessageRole.AGENT, text, id);
    }

    private static List<MessageData> buildMessages(int count) {
        String[] topics = {"Hello", "Hi there", "Let me tell you about work",
                "That is interesting", "Actually, about my trip", "Oh really?",
                "I also wanted to mention", "Great point"};
        List<MessageData> list = new java.util.ArrayList<>();
        for (int i = 0; i < count && i < topics.length; i++) {
            list.add(msg(i, topics[i]));
        }
        return list;
    }
}
