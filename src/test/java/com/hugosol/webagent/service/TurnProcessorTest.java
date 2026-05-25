package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ConversationAgent;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.graph.CoachGraphBuilder;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.nodes.CorrectionNode;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.AgentType;
import com.hugosol.webagent.model.ErrorType;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnProcessorTest {

    @Mock
    private ConversationAgent conversationAgent;

    @Mock
    private SessionService sessionService;

    @Mock
    private CorrectionNode correctionNode;

    @Mock
    private LlmCallLogService llmCallLogService;

    @BeforeEach
    void setUp() {
        when(sessionService.getMessages(anyString())).thenReturn(List.of());
        when(sessionService.getMode(anyString())).thenReturn("WORKPLACE_STANDUP");
        when(sessionService.getUserId(anyString())).thenReturn("user-1");
        when(sessionService.getCorrectionCount(anyString())).thenReturn(0);
        when(sessionService.getTopicMemory(anyString())).thenReturn("");
        when(sessionService.getLearningProfile(anyString())).thenReturn("");
        when(conversationAgent.buildPromptJson(any(), any(AgentMode.class), any(), any(), anyInt()))
                .thenReturn("{\"prompt\":\"test\"}");
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
    }

    @Test
    void conversationErrorFiresCallback() throws Exception {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(5);
            handler.onError(new RuntimeException("model down"));
            return null;
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(), any(), anyInt(), any());

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
        when(correctionNode.apply(any(CoachState.class))).thenReturn(Map.of(CoachState.CORRECTIONS, corrections));

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
        assertThat(received.get(0).getType()).isEqualTo(ErrorType.GRAMMAR);
        assertThat(received.get(0).getMessageId()).isEqualTo(1);
    }

    @Test
    void correctionResultsAreStoredInSessionService() throws Exception {
        setupConversationAgent("ok", 0);
        List<CorrectionData> corrections = List.of(
                new CorrectionData(ErrorType.GRAMMAR, "orig", "corr", "expl")
        );
        when(correctionNode.apply(any(CoachState.class))).thenReturn(Map.of(CoachState.CORRECTIONS, corrections));

        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "test", 1, new StubCallback() {
            @Override
            public void onCorrections(List<CorrectionData> corrs, int msgId) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(sessionService).addCorrections(eq("s1"), any());
    }

    @Test
    void userMessageStoredBeforeProcessing() throws Exception {
        setupConversationAgent("ok", 0);
        TurnProcessor processor = newProcessor();
        CountDownLatch latch = new CountDownLatch(1);

        processor.processTurn("s1", "user text", 3, new StubCallback() {
            @Override
            public void onConversationComplete(String text, int msgId, int tokens) {
                latch.countDown();
            }
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(sessionService).addMessage("s1", MessageRole.USER, "user text", 3, null);
    }

    @Test
    void correctionIncrementalDiffOnlyReturnsNewCorrections() throws Exception {
        setupConversationAgent("ok", 0);

        CorrectionData cdOld = new CorrectionData(ErrorType.GRAMMAR, "old", "oldC", "oldE");
        CorrectionData cdNew = new CorrectionData(ErrorType.CHINGLISH, "new", "newC", "newE");
        List<CorrectionData> allCorrections = new ArrayList<>();
        allCorrections.add(cdOld);
        allCorrections.add(cdNew);

        when(correctionNode.apply(any(CoachState.class))).thenReturn(Map.of(CoachState.CORRECTIONS, allCorrections));

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
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(), any(), anyInt(), any());

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

    private TurnProcessor newProcessor() {
        CoachGraphBuilder builder = new CoachGraphBuilder(correctionNode);
        return new TurnProcessor(builder, conversationAgent, sessionService, llmCallLogService);
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
        }).when(conversationAgent).generateStream(any(), any(AgentMode.class), any(), any(), anyInt(), any());
    }

    private static class StubCallback implements TurnProcessor.TurnCallback {
        @Override
        public void onConversationToken(String delta, int messageId) {
        }

        @Override
        public void onConversationComplete(String fullText, int messageId, int tokenCount) {
        }

        @Override
        public void onCorrections(List<CorrectionData> corrections, int messageId) {
        }

        @Override
        public void onError(String message) {
        }
    }
}
