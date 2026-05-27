package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MemoryContent;
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
    private PromptLoader promptLoader;
    private List<String> receivedTokens;
    private List<ChatMessage> lastMessages;
    private CountDownLatch latch;

    @BeforeEach
    void setUp() {
        promptLoader = new PromptLoader(new DefaultResourceLoader());
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
                new MemoryContent(null, null, null), 0,
                new RecordingHandler());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedTokens).containsExactly("Sounds", " great!");
    }

    @Test
    void systemMessageContainsModeDescription() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        String expected = promptLoader.load("workplace_standup/description.txt");
        String systemContent = getSystemContent();
        assertThat(systemContent).contains(expected);
    }

    @Test
    void workplaceStandupIdentityIsInDescriptionNotSkeleton() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        String description = promptLoader.load("workplace_standup/description.txt");
        String systemContent = getSystemContent();
        assertThat(systemContent).contains(description);
    }

    @Test
    void dailyTalkSystemContentContainsChrisPersona() {
        agent.generateStream(
                List.of(),
                AgentMode.DAILY_TALK,
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        String expected = promptLoader.load("daily_talk/description.txt");
        String systemContent = getSystemContent();
        assertThat(systemContent).contains(expected);
    }

    @Test
    void dailyTalkSystemContentContainsTeachingRules() {
        agent.generateStream(
                List.of(),
                AgentMode.DAILY_TALK,
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        String expected = promptLoader.load("daily_talk/rules.txt");
        String systemContent = getSystemContent();
        assertThat(systemContent).contains(expected);
    }

    @Test
    void systemMessageContainsModeRules() {
        agent.generateStream(
                List.of(),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        String expected = promptLoader.load("workplace_standup/rules.txt");
        String systemContent = getSystemContent();
        assertThat(systemContent).contains(expected);
    }

    @Test
    void userMessagesIncludedInMessageList() {
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "I finished the login module", 1)),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, null), 0,
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
                new MemoryContent(null, null, null), 0,
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
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        assertThat(lastMessages).hasSize(3);
        assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);
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
                new MemoryContent(null, null, null), 0,
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
    void userMemoryInjection_containsTopicAndProfile() {
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent("Talked about travel plans", "Past tense needs work", null),
                0,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).contains("Conversation Memory");
        assertThat(systemContent).contains("Talked about travel plans");
        assertThat(systemContent).contains("Your Learning Profile");
        assertThat(systemContent).contains("Past tense needs work");
        assertThat(systemContent).contains("Active Engagement");
    }

    @Test
    void ragMemoryInjection_containsMemoryCues() {
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, "Work Standup: discussed login module, as well as, Sprint Planning: sprint review notes"),
                0,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).doesNotContain("Conversation Memory");
        assertThat(systemContent).doesNotContain("Your Learning Profile");
        assertThat(systemContent).contains("Memory Cues");
        assertThat(systemContent).contains("login module");
        assertThat(systemContent).contains("sprint review");
        assertThat(systemContent).contains("Active Engagement");
    }

    @Test
    void emptyMemoryContent_noInjection() {
        agent.generateStream(
                List.of(new MessageData(MessageRole.USER, "Hi", 0)),
                AgentMode.WORKPLACE_STANDUP,
                new MemoryContent(null, null, null),
                0,
                new NoopHandler());

        String systemContent = getSystemContent();
        assertThat(systemContent).doesNotContain("Conversation Memory");
        assertThat(systemContent).doesNotContain("Memory Cues");
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
                new MemoryContent(null, null, null), 0,
                new NoopHandler());

        assertThat(lastMessages).hasSize(2);
    }

    @Test
    void applyTimeLabelsToCues_addsTimePrefixToEachCue() {
        var now = java.time.LocalDateTime.of(2026, 5, 28, 12, 0);
        var yesterday = now.minusDays(1);
        var lastWeek = now.minusDays(5);

        String result = ConversationAgent.applyTimeLabelsToCues(
                "Work Standup: discussed login, as well as, Travel: Japan trip",
                List.of(yesterday, lastWeek));

        assertThat(result).isEqualTo(
                "[from yesterday] Work Standup: discussed login, as well as, [from a few days ago] Travel: Japan trip");
    }

    @Test
    void applyTimeLabelsToCues_skipsNullCreatedAt() {
        var yesterday = java.time.LocalDateTime.of(2026, 5, 27, 12, 0);

        String result = ConversationAgent.applyTimeLabelsToCues(
                "Topic A: details, as well as, Topic B: details",
                java.util.Arrays.asList(yesterday, null));

        assertThat(result).isEqualTo(
                "[from yesterday] Topic A: details, as well as, Topic B: details");
    }

    @Test
    void applyTimeLabelsToCues_emptyCueListReturnsOriginal() {
        String result = ConversationAgent.applyTimeLabelsToCues(
                "Topic: details", List.of());

        assertThat(result).isEqualTo("Topic: details");
    }

    @Test
    void applyTimeLabelsToCues_nullCueListReturnsOriginal() {
        String result = ConversationAgent.applyTimeLabelsToCues(
                "Topic: details", null);

        assertThat(result).isEqualTo("Topic: details");
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
