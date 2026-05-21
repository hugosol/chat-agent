package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.model.PersonaType;
import com.hugosol.webagent.model.ScenarioType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class
ConversationAgentTest {

    private ConversationAgent agent;
    private List<String> receivedTokens;
    private String lastPrompt;
    private CountDownLatch latch;

    @BeforeEach
    void setUp() {
        PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());
        receivedTokens = new ArrayList<>();
        latch = new CountDownLatch(1);

        StubStreamingModel model = new StubStreamingModel() {
            @Override
            public void chat(String prompt, StreamingChatResponseHandler handler) {
                lastPrompt = prompt;
                handler.onPartialResponse("Sounds");
                handler.onPartialResponse(" great!");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Sounds great!"))
                        .tokenUsage(new TokenUsage(50, 30, 20))
                        .build());
            }
        };
        agent = new ConversationAgent(model, promptLoader);
    }

    @Test
    void streamingTokensArriveInOrder() throws Exception {
        agent.generateStream("I finished the task",
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        receivedTokens.add(token);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                    }
                });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedTokens).containsExactly("Sounds", " great!");
    }

    @Test
    void promptContainsPersonaRole() {
        agent.generateStream("test",
                List.of(),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).contains("team colleague");
    }

    @Test
    void promptContainsScenarioDescription() {
        agent.generateStream("test",
                List.of(),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).contains("standup meeting");
    }

    @Test
    void promptContainsUserInput() {
        agent.generateStream("I finished the login module",
                List.of(),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).contains("I finished the login module");
    }

    @Test
    void emptyHistoryShowsPlaceholder() {
        agent.generateStream("test",
                List.of(),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).contains("No previous messages");
    }

    @Test
    void historyIsFormattedInPrompt() {
        List<MessageData> history = List.of(
                new MessageData(MessageRole.USER, "Hello", 1),
                new MessageData(MessageRole.AGENT, "Hi there", 1)
        );

        agent.generateStream("Good",
                history,
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).contains("USER: Hello");
        assertThat(lastPrompt).contains("AGENT: Hi there");
    }

    @Test
    void historyTruncatedToLast20() {
        List<MessageData> history = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            history.add(new MessageData(MessageRole.USER, "msg-" + String.format("%02d", i), i));
        }

        agent.generateStream("now",
                history,
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new NoopHandler());

        assertThat(lastPrompt).doesNotContain("msg-01");
        assertThat(lastPrompt).doesNotContain("msg-05");
        assertThat(lastPrompt).contains("msg-06");
        assertThat(lastPrompt).contains("msg-25");
    }

    @Test
    void errorCallbackFiresOnStreamingError() throws Exception {
        StubStreamingModel errorModel = new StubStreamingModel() {
            @Override
            public void chat(String prompt, StreamingChatResponseHandler handler) {
                handler.onError(new RuntimeException("model error"));
            }
        };
        ConversationAgent errorAgent = new ConversationAgent(errorModel,
                new PromptLoader(new DefaultResourceLoader()));

        latch = new CountDownLatch(1);
        errorAgent.generateStream("test", List.of(),
                ScenarioType.WORKPLACE_STANDUP.name(),
                PersonaType.TEAM_COLLEAGUE.name(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                    }

                    @Override
                    public void onError(Throwable error) {
                        assertThat(error.getMessage()).contains("model error");
                        latch.countDown();
                    }
                });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private static class NoopHandler implements StreamingChatResponseHandler {
        @Override
        public void onPartialResponse(String token) {
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    @SuppressWarnings("removal")
    private abstract static class StubStreamingModel implements StreamingChatLanguageModel {
        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        }
    }
}
