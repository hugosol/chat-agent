package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ConversationAgent;
import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CoachGraphBuilder;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import com.hugosol.webagent.model.AgentType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GraphExecutionService {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutionService.class);

    private final CompiledGraph<CoachState> graph;
    private final ConversationAgent conversationAgent;
    private final ReportAgent reportAgent;
    private final TokenTracker tokenTracker;
    private final Map<String, CoachState> activeStates = new ConcurrentHashMap<>();

    public interface TurnCallback {
        void onConversationToken(String delta, int messageId);
        void onConversationComplete(String fullText, int messageId, int tokenCount);
        void onCorrections(List<CorrectionData> corrections, int messageId);
        void onError(String message);
    }

    public GraphExecutionService(CoachGraphBuilder graphBuilder,
                                  ConversationAgent conversationAgent,
                                  ReportAgent reportAgent,
                                  TokenTracker tokenTracker) {
        this.graph = graphBuilder.getCompiledGraph();
        this.conversationAgent = conversationAgent;
        this.reportAgent = reportAgent;
        this.tokenTracker = tokenTracker;
    }

    public void initSession(String sessionId, String scenario, String persona) {
        Map<String, Object> initData = CoachState.initialState(sessionId, scenario, persona);
        var state = new CoachState(initData);
        activeStates.put(sessionId, state);
        tokenTracker.initSession(sessionId);
        log.info("GraphExecutionService: initialized session {}", sessionId);
    }

    public void processTurn(String sessionId, String userInput, int messageId, TurnCallback callback) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) {
            callback.onError("No active session: " + sessionId);
            return;
        }

        MessageData userMessage = new MessageData("USER", userInput);
        state.messages().add(userMessage);
        int correctionsBefore = state.corrections().size();

        List<MessageData> historySnapshot = new ArrayList<>(state.messages());

        CompletableFuture.runAsync(() -> {
            StringBuilder fullText = new StringBuilder();

            conversationAgent.generateStream(userInput, historySnapshot, state.scenario(), state.persona(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            fullText.append(token);
                            callback.onConversationToken(token, messageId);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            String agentText = fullText.toString();
                            int tokens = (response != null && response.tokenUsage() != null)
                                    ? response.tokenUsage().totalTokenCount() : 0;

                            synchronized (state) {
                                state.messages().add(new MessageData("AGENT", agentText));
                                tokenTracker.addTokens(sessionId, AgentType.CONVERSATION, tokens);
                            }

                            callback.onConversationComplete(agentText, messageId, tokens);
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("ConversationAgent error", error);
                            callback.onError("Conversation error: " + error.getMessage());
                        }
                    });
        });

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> input = Map.of(CoachState.USER_INPUT, userInput);
                var config = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();

                CoachState finalState = null;
                for (var item : graph.stream(input, config)) {
                    finalState = item.state();
                }

                if (finalState != null) {
                    List<CorrectionData> allCorrections = finalState.corrections();
                    List<CorrectionData> newOnly = allCorrections.subList(
                            Math.min(correctionsBefore, allCorrections.size()),
                            allCorrections.size());

                    for (CorrectionData c : newOnly) {
                        c.setMessageId(messageId);
                    }

                    synchronized (state) {
                        state.corrections().addAll(newOnly);
                    }

                    callback.onCorrections(new ArrayList<>(newOnly), messageId);
                }
            } catch (Exception e) {
                log.error("Correction graph error", e);
                callback.onError("Correction error: " + e.getMessage());
            }
        });
    }

    public ReportResult generateReport(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No active session: " + sessionId);
        }
        List<MessageData> messages = new ArrayList<>(state.messages());
        List<CorrectionData> corrections = new ArrayList<>(state.corrections());
        return reportAgent.generate(messages, corrections);
    }

    public CoachState getSessionState(String sessionId) {
        return activeStates.get(sessionId);
    }

    public void removeSession(String sessionId) {
        activeStates.remove(sessionId);
        tokenTracker.removeSession(sessionId);
        log.info("GraphExecutionService: removed session {}", sessionId);
    }
}
