package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.ConversationAgent;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.CueMatch;
import com.hugosol.chatagent.dto.MemoryContent;
import com.hugosol.chatagent.dto.MemoryCueQueue;
import com.hugosol.chatagent.graph.ChatGraphBuilder;
import com.hugosol.chatagent.graph.ChatState;
import com.hugosol.chatagent.graph.nodes.CorrectionNode;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.AgentType;
import com.hugosol.chatagent.model.ErrorType;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.MemoryCueRepository;
import com.hugosol.chatagent.service.UserPreferencesService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnProcessorTest {

    @Mock
    private ConversationAgent conversationAgent;

    @Mock
    private SessionService sessionService;

    @Mock
    private CorrectionNode correctionNode;

    @Mock
    private LlmCallLogService llmCallLogService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private LearningProfileService learningProfileService;

    @Mock
    private MemoryCueRepository memoryCueRepository;

    @Mock
    private UserPreferencesService userPreferencesService;

    private AppProperties appProperties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        when(sessionService.getMessages(anyString())).thenReturn(List.of());
        when(sessionService.getMode(anyString())).thenReturn("WORKPLACE_STANDUP");
        when(sessionService.getUserId(anyString())).thenReturn("user1");
        when(sessionService.getCorrectionCount(anyString())).thenReturn(0);
        when(sessionService.getLearningProfile(anyString())).thenReturn("");
        when(sessionService.getMemoryCueQueue(anyString())).thenReturn(new MemoryCueQueue(3));
        when(conversationAgent.buildPromptJson(any(), any(AgentMode.class), any(MemoryContent.class), anyString(), anyInt()))
                .thenReturn("[{\"role\":\"system\",\"content\":\"You are a coach\"},{\"role\":\"user\",\"content\":\"hi\"},{\"role\":\"assistant\",\"content\":\"Hello\"}]");

        UserPreferences prefs = new UserPreferences("user1");
        prefs.setUtcOffset(8);
        when(userPreferencesService.get("user1")).thenReturn(prefs);
    }

    @Test
    void conversationStreamingTokensReachCallback() throws Exception {
        setupConversationAgent("Hello", 10);
        TurnProcessor processor = newProcessor();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> tokens = new ArrayList<>();

        processor.processTurn("s1", "hi", 1, new StubCallback() {
            @Override
            public void onConversationToken(String delta, int msgId) {
                tokens.add(delta);
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(tokens).contains("Hello");
    }

    @Test
    void conversationCompleteStoresAgentMessage() throws Exception {
        setupConversationAgent("Hi", 6);
        TurnProcessor processor = newProcessor();

        CountDownLatch latch = new CountDownLatch(1);
        processor.processTurn("s1", "hello", 1, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(sessionService).addMessage("s1", MessageRole.AGENT, "Hi", 1, 6);
        verify(sessionService).recordTokens("s1", AgentType.CONVERSATION, 6);
        verify(llmCallLogService).saveAsync(
                eq("s1"), eq("user1"), eq("CONVERSATION"), eq("WORKPLACE_STANDUP"),
                contains("system"), contains("You are a coach"), contains("user"),
                contains("Hi"), anyInt(), anyInt(), anyLong(), eq("SUCCESS"), isNull());
    }

    @Test
    void conversationErrorFiresCallback() throws Exception {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(5);
            handler.onError(new RuntimeException("model down"));
            return null;
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(MemoryContent.class), anyString(), anyInt(), any());

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "hello", 1, new StubCallback() {
            @Override
            public void onError(String msg) {
                assertThat(msg).contains("model down");
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void correctionGraphResultsReachCallback() throws Exception {
        setupConversationAgent("ok", 0);
        List<CorrectionData> corrections = List.of(
                new CorrectionData(ErrorType.GRAMMAR, "orig", "corr", "expl")
        );
        when(correctionNode.apply(any(ChatState.class))).thenReturn(Map.of(ChatState.CORRECTIONS, corrections));

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);
        List<CorrectionData> received = new ArrayList<>();

        processor.processTurn("s1", "test", 1, new StubCallback() {
            @Override
            public void onCorrections(List<CorrectionData> corrs, int msgId) {
                received.addAll(corrs);
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessageId()).isEqualTo(1);
    }

    @Test
    void messageIdOne_callsRagSearch() throws Exception {
        setupConversationAgent("ok", 0);
        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "hi", 1, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(eq("hi"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6));
    }

    @Test
    void messageIdFive_callsRagSearch() throws Exception {
        setupConversationAgent("ok", 0);
        when(embeddingService.search(eq("tell me about work"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), anyInt(), anyDouble()))
                .thenReturn(List.of(new CueMatch("cue-1", "Work Standup", "Discussed login", 0.85,
                        java.time.Instant.parse("2026-05-27T10:00:00Z"))));

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "tell me about work", 5, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(eq("tell me about work"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6));
    }

    @Test
    void messageIdFive_noSearchResults_passesEmptyMemoryCues() throws Exception {
        setupConversationAgent("ok", 0);
        when(embeddingService.search(anyString(), any(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "completely new topic", 5, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(anyString(), any(), anyString(), anyInt(), anyDouble());
    }

    @Test
    void correctionIncrementalDiffOnlyReturnsNewCorrections() throws Exception {
        setupConversationAgent("ok", 0);

        CorrectionData cdOld = new CorrectionData(ErrorType.GRAMMAR, "old", "oldC", "oldE");
        CorrectionData cdNew = new CorrectionData(ErrorType.CHINGLISH, "new", "newC", "newE");
        List<CorrectionData> allCorrections = new ArrayList<>();
        allCorrections.add(cdOld);
        allCorrections.add(cdNew);

        when(correctionNode.apply(any(ChatState.class))).thenReturn(Map.of(ChatState.CORRECTIONS, allCorrections));
        when(sessionService.getCorrectionCount("s1")).thenReturn(1);

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);
        List<CorrectionData> received = new ArrayList<>();

        processor.processTurn("s1", "test", 1, new StubCallback() {
            @Override
            public void onCorrections(List<CorrectionData> corrs, int msgId) {
                received.addAll(corrs);
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getType()).isEqualTo(ErrorType.CHINGLISH);
    }

    @Test
    void nullChatResponseGuardDoesNotThrow() throws Exception {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(5);
            handler.onPartialResponse("x");
            handler.onCompleteResponse(null);
            return null;
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(MemoryContent.class), anyString(), anyInt(), any());

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "hello", 1, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                assertThat(tokens).isZero();
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void registersPendingCorrectionFutureWithSessionService() throws Exception {
        setupConversationAgent("ok", 0);
        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "test", 1, new StubCallback() {
            @Override
            public void onCorrections(List<CorrectionData> corrs, int msgId) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(sessionService).addPendingCorrection(eq("s1"), any(CompletableFuture.class));
    }

    @Test
    void messageIdTwo_firstRagLoad_searchesTopK() throws Exception {
        setupConversationAgent("ok", 0);
        var cueA = new CueMatch("a", "Topic A", "Summary A", 0.6,
                java.time.Instant.parse("2026-05-28T10:00:00Z"));
        var cueB = new CueMatch("b", "Topic B", "Summary B", 0.75,
                java.time.Instant.parse("2026-05-28T10:00:00Z"));
        var cueC = new CueMatch("c", "Topic C", "Summary C", 0.9,
                java.time.Instant.parse("2026-05-28T10:00:00Z"));
        when(embeddingService.search(eq("my input"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6)))
                .thenReturn(List.of(cueA, cueB, cueC));

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "my input", 2, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(eq("my input"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6));
    }

    @Test
    void messageIdTwo_firstRagLoad_emptyResults_stillCallsSearch() throws Exception {
        setupConversationAgent("ok", 0);
        when(embeddingService.search(eq("new topic"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6)))
                .thenReturn(List.of());

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "new topic", 2, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(eq("new topic"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6));
    }

    @Test
    void messageIdThree_subsequentRagLoad_searchesTopK() throws Exception {
        setupConversationAgent("ok", 0);
        var nonEmptyQueue = new MemoryCueQueue(3);
        nonEmptyQueue.push(List.of(
                new CueMatch("a", "A", "A", 0.6, java.time.Instant.parse("2026-05-28T10:00:00Z")),
                new CueMatch("b", "B", "B", 0.75, java.time.Instant.parse("2026-05-28T10:00:00Z")),
                new CueMatch("c", "C", "C", 0.9, java.time.Instant.parse("2026-05-28T10:00:00Z"))
        ));
        when(sessionService.getMemoryCueQueue("s1")).thenReturn(nonEmptyQueue);

        var cueD = new CueMatch("d", "D", "D", 0.7, java.time.Instant.parse("2026-05-29T10:00:00Z"));
        var cueE = new CueMatch("e", "E", "E", 0.85, java.time.Instant.parse("2026-05-29T10:00:00Z"));
        when(embeddingService.search(eq("follow up"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6)))
                .thenReturn(List.of(cueD, cueE));

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "follow up", 3, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(embeddingService).search(eq("follow up"), eq(AgentMode.WORKPLACE_STANDUP), eq("user1"), eq(2), eq(0.6));
    }


    @Test
    void japaneseModeDoesNotInvokeCorrection() throws Exception {
        setupConversationAgent("こんにちは", 8);
        when(sessionService.getMode("s1")).thenReturn("JAPANESE_BUSINESS");
        TurnProcessor processor = newProcessor();

        CountDownLatch latch = new CountDownLatch(1);
        processor.processTurn("s1", "こんにちは", 1, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(correctionNode, never()).apply(any(ChatState.class));
        verify(sessionService, never()).addPendingCorrection(eq("s1"), any());
    }
    private TurnProcessor newProcessor() {
        ChatGraphBuilder builder = new ChatGraphBuilder(correctionNode);
        return new TurnProcessor(builder, conversationAgent, sessionService, llmCallLogService, embeddingService, learningProfileService, memoryCueRepository, appProperties, userPreferencesService, executor);
    }

    private void setupConversationAgent(String responseText, int totalTokens) {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(5);
            handler.onPartialResponse(responseText);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from(responseText))
                    .tokenUsage(new TokenUsage(totalTokens / 2, totalTokens / 2, totalTokens))
                    .build());
            return null;
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(MemoryContent.class), anyString(), anyInt(), any());
    }

    private static class StubCallback implements TurnProcessor.TurnCallback {
        @Override
        public void onConversationToken(String delta, int messageId) {}
        @Override
        public void onConversationComplete(String fullText, int messageId, int tokenCount) {}
        @Override
        public void onCorrections(List<CorrectionData> corrections, int messageId) {}
        @Override
        public void onError(String message) {}
    }
}
