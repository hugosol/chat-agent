package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ConversationAgent;
import com.hugosol.webagent.graph.CoachGraphBuilder;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentType;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class TurnProcessor {

    private static final Logger log = LoggerFactory.getLogger(TurnProcessor.class);

    private final CompiledGraph<CoachState> graph;
    private final ConversationAgent conversationAgent;
    private final SessionService sessionService;

    public interface TurnCallback {
        void onConversationToken(String delta, int messageId);
        void onConversationComplete(String fullText, int messageId, int tokenCount);
        void onCorrections(List<CorrectionData> corrections, int messageId);
        void onError(String message);
    }

    public TurnProcessor(CoachGraphBuilder graphBuilder,
                         ConversationAgent conversationAgent,
                         SessionService sessionService) {
        this.graph = graphBuilder.getCompiledGraph();
        this.conversationAgent = conversationAgent;
        this.sessionService = sessionService;
    }

    public void processTurn(String sessionId, String userInput, int messageId, TurnCallback callback) {
        sessionService.addMessage(sessionId, MessageRole.USER, userInput, messageId);

        int correctionsBefore = sessionService.getCorrectionCount(sessionId);
        List<MessageData> historySnapshot = sessionService.getMessages(sessionId);
        String scenario = sessionService.getScenario(sessionId);
        String persona = sessionService.getPersona(sessionId);

        CompletableFuture.runAsync(() -> {
            StringBuilder fullText = new StringBuilder();

            conversationAgent.generateStream(userInput, historySnapshot, scenario, persona,
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

                            sessionService.addMessage(sessionId, MessageRole.AGENT, agentText, messageId);
                            sessionService.recordTokens(sessionId, AgentType.CONVERSATION, tokens);

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

                    sessionService.addCorrections(sessionId, newOnly);

                    callback.onCorrections(new ArrayList<>(newOnly), messageId);
                }
            } catch (Exception e) {
                log.error("Correction graph error", e);
                callback.onError("Correction error: " + e.getMessage());
            }
        });
    }
}
