package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ConversationAgent;
import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.dto.CueMatch;
import com.hugosol.webagent.dto.MemoryContent;
import com.hugosol.webagent.graph.CoachGraphBuilder;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.AgentType;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

@Component
public class TurnProcessor {

    private static final Logger log = LoggerFactory.getLogger(TurnProcessor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CompiledGraph<CoachState> graph;
    private final ConversationAgent conversationAgent;
    private final SessionService sessionService;
    private final LlmCallLogService llmCallLogService;
    private final EmbeddingService embeddingService;
    private final MemoryService memoryService;
    private final AppProperties appProperties;
    private final ExecutorService executor;

    public interface TurnCallback {
        void onConversationToken(String delta, int messageId);
        void onConversationComplete(String fullText, int messageId, int tokenCount);
        void onCorrections(List<CorrectionData> corrections, int messageId);
        void onError(String message);
    }

    public TurnProcessor(CoachGraphBuilder graphBuilder,
                          ConversationAgent conversationAgent,
                          SessionService sessionService,
                          LlmCallLogService llmCallLogService,
                          EmbeddingService embeddingService,
                          MemoryService memoryService,
                          AppProperties appProperties,
                          @org.springframework.beans.factory.annotation.Qualifier("llmRequestExecutor") ExecutorService executor) {
        this.graph = graphBuilder.getCompiledGraph();
        this.conversationAgent = conversationAgent;
        this.sessionService = sessionService;
        this.llmCallLogService = llmCallLogService;
        this.embeddingService = embeddingService;
        this.memoryService = memoryService;
        this.appProperties = appProperties;
        this.executor = executor;
    }

    public void processTurn(String sessionId, String userInput, int messageId, TurnCallback callback) {
        sessionService.addMessage(sessionId, MessageRole.USER, userInput, messageId, null);

        int correctionsBefore = sessionService.getCorrectionCount(sessionId);
        List<MessageData> history = sessionService.getMessages(sessionId);
        AgentMode mode = resolveMode(sessionId);
        String userId = sessionService.getUserId(sessionId);
        MemoryContent memoryContent = resolveMemoryContext(sessionId, userInput, messageId, mode, userId);

        startConversation(sessionId, history, mode, memoryContent, messageId, userId, callback);

        CompletableFuture<Void> correctionFuture = startCorrection(sessionId, userInput, messageId, correctionsBefore, callback);
        sessionService.addPendingCorrection(sessionId, correctionFuture);
    }

    private AgentMode resolveMode(String sessionId) {
        try {
            return AgentMode.valueOf(sessionService.getMode(sessionId));
        } catch (IllegalArgumentException e) {
            return AgentMode.WORKPLACE_STANDUP;
        }
    }

    private MemoryContent resolveMemoryContext(String sessionId, String userInput, int messageId,
                                                AgentMode mode, String userId) {
        String topicSummary = sessionService.getTopicMemory(sessionId);
        String learningProfile = sessionService.getLearningProfile(sessionId);

        int userMemoryRounds = appProperties.getMemory().getUserMemoryRounds();

        if (messageId <= userMemoryRounds) {
            LocalDateTime topicCreatedAt = memoryService.loadTopicCreatedAt(userId, mode);
            return new MemoryContent(topicSummary, learningProfile, null, topicCreatedAt, null);
        }

        int topK = appProperties.getMemory().getRetrieval().getTopK();
        double threshold = appProperties.getMemory().getRetrieval().getSimilarityThreshold();
        List<CueMatch> ragResults = embeddingService.search(userInput, mode, userId, topK, threshold);
        if (!ragResults.isEmpty()) {
            String memoryCuesText = ragResults.stream()
                    .map(m -> m.topic() + ": " + m.summary())
                    .collect(Collectors.joining(", as well as, "));
            List<LocalDateTime> cueCreatedAts = ragResults.stream()
                    .map(CueMatch::createdAt)
                    .collect(Collectors.toList());
            log.info("TurnProcessor: RAG retrieved {} cues for session {} messageId {}",
                    ragResults.size(), sessionId, messageId);
            return new MemoryContent(null, null, memoryCuesText, null, cueCreatedAts);
        }
        return new MemoryContent(null, null, null, null, null);
    }

    private void startConversation(String sessionId, List<MessageData> history, AgentMode mode,
                                    MemoryContent memoryContent, int messageId, String userId, TurnCallback callback) {
        String promptJson = conversationAgent.buildPromptJson(history, mode, memoryContent, messageId);
        long startTime = System.currentTimeMillis();
        StringBuilder fullText = new StringBuilder();
        String modeName = mode.name();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
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

                if (response != null) {
                    int inputTokens = (response.tokenUsage() != null)
                            ? response.tokenUsage().inputTokenCount() : 0;
                    int outputTokens = (response.tokenUsage() != null)
                            ? response.tokenUsage().outputTokenCount() : 0;
                    long duration = System.currentTimeMillis() - startTime;

                    String[] split = splitPromptJson(promptJson);
                    String systemPrompt = split[0];
                    String chatHistory = split[1];

                    llmCallLogService.saveAsync(sessionId, userId, "CONVERSATION", modeName,
                            promptJson, systemPrompt, chatHistory,
                            agentText, inputTokens, outputTokens,
                            duration, "SUCCESS", null);
                }

                sessionService.addMessage(sessionId, MessageRole.AGENT, agentText, messageId, tokens);
                sessionService.recordTokens(sessionId, AgentType.CONVERSATION, tokens);

                callback.onConversationComplete(agentText, messageId, tokens);
            }

            @Override
            public void onError(Throwable error) {
                log.error("ConversationAgent error", error);
                callback.onError("Conversation error: " + error.getMessage());
            }
        };

        conversationAgent.generateStream(history, mode, memoryContent, messageId, handler);
    }

    private CompletableFuture<Void> startCorrection(String sessionId, String userInput, int messageId,
                                                     int correctionsBefore, TurnCallback callback) {
        return CompletableFuture.runAsync(() -> {
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
        }, executor);
    }

    static String[] splitPromptJson(String promptJson) {
        try {
            JsonNode array = mapper.readTree(promptJson);
            String systemPrompt = null;
            List<Map<String, String>> historyEntries = new ArrayList<>();
            for (JsonNode node : array) {
                String role = node.has("role") ? node.get("role").asText() : "";
                String content = node.has("content") ? node.get("content").asText() : "";
                if ("system".equals(role)) {
                    systemPrompt = content;
                } else if (!role.isEmpty()) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("role", role);
                    entry.put("content", content);
                    historyEntries.add(entry);
                }
            }
            String chatHistory = historyEntries.isEmpty() ? null : mapper.writeValueAsString(historyEntries);
            return new String[]{ systemPrompt, chatHistory };
        } catch (Exception e) {
            log.warn("TurnProcessor: failed to parse prompt JSON for logging: {}", e.getMessage());
            return new String[]{ null, null };
        }
    }
}
