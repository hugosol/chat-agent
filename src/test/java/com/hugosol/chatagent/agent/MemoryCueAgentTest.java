package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MessageRole;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MemoryCueAgentTest {

    private MemoryCueAgent agent;
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
        agent = new MemoryCueAgent(llmReqConstructor, promptLoader, new AppProperties());
    }

    private String lastUserContent() {
        assertThat(chatModel.lastMessages).isNotNull();
        return ((UserMessage) chatModel.lastMessages.get(chatModel.lastMessages.size() - 1)).singleText();
    }

    @Test
    void detectSwitches_returnsEmptyListWhenNoSwitch() {
        chatModel.setResponse("[]");

        List<Integer> result = agent.detectSwitches(List.of(
                msg(0, "Hello"), msg(1, "Hi there")
        ), AgentMode.WORKPLACE_STANDUP, new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result).isEmpty();
    }

    @Test
    void detectSwitches_returnsSingleSwitchIndex() {
        chatModel.setResponse("[3]");

        List<Integer> result = agent.detectSwitches(buildMessages(6), AgentMode.DAILY_TALK,
                new TaskContext("s1", "u1", "DAILY_TALK"));

        assertThat(result).containsExactly(3);
    }

    @Test
    void detectSwitches_returnsMultipleSwitchIndexes() {
        chatModel.setResponse("[2, 5]");

        List<Integer> result = agent.detectSwitches(buildMessages(8), AgentMode.WORKPLACE_STANDUP,
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result).containsExactly(2, 5);
    }

    @Test
    void detectSwitches_handlesExtraTextBeforeJson() {
        chatModel.setResponse("The topic switches at these points: [3]");

        List<Integer> result = agent.detectSwitches(buildMessages(6), AgentMode.WORKPLACE_STANDUP,
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result).containsExactly(3);
    }

    @Test
    void generateCue_returnsTopicSummary() {
        chatModel.setResponse("{\"topic\": \"Travel\", \"summary\": \"Talked about Japan trip\"}");

        var result = agent.generateCue(
                List.of(msg(0, "I went to Japan"), msg(1, "That sounds fun")),
                AgentMode.WORKPLACE_STANDUP, 0,
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        assertThat(result.topic()).isEqualTo("Travel");
        assertThat(result.summary()).isEqualTo("Talked about Japan trip");
    }

    @Test
    void generateCue_throwsOnInvalidJson() {
        chatModel.setResponse("not valid json");

        assertThatThrownBy(() -> agent.generateCue(
                List.of(msg(0, "Hi")), AgentMode.DAILY_TALK, 0,
                new TaskContext("s1", "u1", "DAILY_TALK")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void detectSwitches_injectsXmlFormatInPrompt() {
        chatModel.setResponse("[]");

        agent.detectSwitches(buildMessages(4), AgentMode.WORKPLACE_STANDUP,
                new TaskContext("s1", "u1", "WORKPLACE_STANDUP"));

        String userContent = lastUserContent();
        assertThat(userContent).contains("<turn role=\"user\">");
        assertThat(userContent).contains("<turn role=\"assistant\">");
        assertThat(userContent).doesNotContain("[MSG#");
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
