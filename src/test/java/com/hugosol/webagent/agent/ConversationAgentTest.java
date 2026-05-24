package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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

class ConversationAgentTest {

    private ConversationAgent agent;
    private List<String> receivedTokens;
    private List<ChatMessage> lastMessages;
    private CountDownLatch latch;

    @BeforeEach
    void setUp() {
        PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());
        receivedTokens = new ArrayList<>();
        latch = new CountDownLatch(1);

        StubStreamingModel model = new StubStreamingModel() {
            @Override
            public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
                lastMessages = messages;
                handler.onNext("Sounds");
                handler.onNext(" great!");
                handler.onComplete(new dev.langchain4j.model.output.Response<>(
                        AiMessage.from("Sounds great!"),
                        new TokenUsage(50, 30, 20),
                        null));
            }

            @Override
            public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
                lastMessages = messages;
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
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                new RecordingHandler());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedTokens).containsExactly("Sounds", " great!");
    }

    @Test
    void systemMessageContainsModeDescription() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("friendly teammate");
        assertThat(systemContent).contains("daily standup");
    }

    @Test
    void workplaceStandupIdentityIsInDescriptionNotSkeleton() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("friendly teammate");
        assertThat(systemContent).contains("software engineer");
        assertThat(systemContent).contains("practice workplace English");
    }

    @Test
    void dailyTalkSystemContentContainsChrisPersona() {
        agent.generateStream(
                List.of(),
                AgentMode.DAILY_TALK,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("Chris");
        assertThat(systemContent).contains("English-speaking");
        assertThat(systemContent).contains("cultural");
        assertThat(systemContent).contains("voice call");
    }

    @Test
    void dailyTalkSystemContentContainsTeachingRules() {
        agent.generateStream(
                List.of(),
                AgentMode.DAILY_TALK,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("unnatural phrase");
        assertThat(systemContent).contains("culturally specific");
        assertThat(systemContent).contains("open-ended questions");
        assertThat(systemContent).contains("varying naturally");
    }

    @Test
    void systemMessageContainsModeRules() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("encouraging and supportive");
        assertThat(systemContent).contains("varying naturally");
    }

    @Test
    void userMessagesIncludedInMessageList() {
        List<MessageData> history = List.of(
                new MessageData(MessageRole.USER, "I finished the login module", 1)
        );

        agent.generateStream(history,
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        assertThat(lastMessages).isNotNull();
        assertThat(lastMessages).hasSizeGreaterThanOrEqualTo(2);
        assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);

        String userContent = lastMessages.get(lastMessages.size() - 1).toString();
        assertThat(userContent).contains("I finished the login module");
    }

    @Test
    void emptyHistoryHasOnlySystemMessage() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        assertThat(lastMessages).isNotNull();
        assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void historyMessagesIncludeUserAndAgentRoles() {
        List<MessageData> history = List.of(
                new MessageData(MessageRole.USER, "Hello", 1),
                new MessageData(MessageRole.AGENT, "Hi there", 1)
        );

        agent.generateStream(history,
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        assertThat(lastMessages).hasSize(3); // system + user + assistant
        assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void historyTruncatedToLast20() {
        List<MessageData> history = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            history.add(new MessageData(MessageRole.USER, "msg-" + String.format("%02d", i), i));
        }

        agent.generateStream(history,
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        assertThat(lastMessages).hasSize(21); // system + 20 user messages
        String firstUserContent = lastMessages.get(1).toString();
        assertThat(firstUserContent).doesNotContain("msg-01");
        assertThat(firstUserContent).contains("msg-06");
        String lastUserContent = lastMessages.get(20).toString();
        assertThat(lastUserContent).contains("msg-25");
    }

    @Test
    void errorCallbackFiresOnStreamingError() throws Exception {
        StubStreamingModel errorModel = new StubStreamingModel() {
            @Override
            public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
                lastMessages = messages;
                handler.onError(new RuntimeException("model error"));
            }
        };
        ConversationAgent errorAgent = new ConversationAgent(errorModel,
                new PromptLoader(new DefaultResourceLoader()));

        latch = new CountDownLatch(1);
        errorAgent.generateStream(List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {}
                    @Override
                    public void onCompleteResponse(ChatResponse response) {}

                    @Override
                    public void onError(Throwable error) {
                        assertThat(error.getMessage()).contains("model error");
                        latch.countDown();
                    }
                });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void generateStreamFirstTurn_injectsMemoryContent() {
        agent.generateStreamFirstTurn(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                "Talked about travel plans",
                "Past tense needs work",
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("Conversation Memory");
        assertThat(systemContent).contains("Talked about travel plans");
        assertThat(systemContent).contains("Your Learning Profile");
        assertThat(systemContent).contains("Past tense needs work");
        assertThat(systemContent).contains("Active Engagement");
        assertThat(systemContent).contains("unfinished topic");
    }

    @Test
    void generateStream_hasNoMemoryContent() {
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).doesNotContain("Conversation Memory");
        assertThat(systemContent).doesNotContain("Active Engagement");
    }

    @Test
    void correctionRoleMessagesAreSkipped() {
        List<MessageData> history = List.of(
                new MessageData(MessageRole.USER, "Hello", 1),
                new MessageData(MessageRole.CORRECTION, "correction text", 1)
        );

        agent.generateStream(history,
                AgentMode.WORKPLACE_STANDUP,
                new NoopHandler());

        assertThat(lastMessages).hasSize(2); // system + user only, correction skipped
    }

    private String getSystemContent() {
        if (lastMessages != null && !lastMessages.isEmpty()) {
            ChatMessage first = lastMessages.get(0);
            if (first instanceof SystemMessage sm) {
                return sm.text();
            }
            return first.toString();
        }
        return "";
    }

    private class RecordingHandler implements StreamingChatResponseHandler {
        @Override
        public void onPartialResponse(String token) {
            receivedTokens.add(token);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) {}
    }

    private static class NoopHandler implements StreamingChatResponseHandler {
        @Override
        public void onPartialResponse(String token) {}
        @Override
        public void onCompleteResponse(ChatResponse response) {}
        @Override
        public void onError(Throwable error) {}
    }

    @SuppressWarnings("removal")
    private abstract static class StubStreamingModel implements StreamingChatLanguageModel {
        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {}
    }
}
