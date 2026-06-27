package com.hugosol.chatagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.chatagent.agent.common.*;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.AssertionGroupRepository;
import com.hugosol.chatagent.repository.AssertionLineageRepository;
import com.hugosol.chatagent.repository.MemoryAssertionRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class AssertionService {

    private static final Logger log = LoggerFactory.getLogger(AssertionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final LlmReqConstructor llmReqConstructor;
    private final MemoryAssertionRepository assertionRepository;
    private final AssertionGroupRepository groupRepository;
    private final AssertionLineageRepository lineageRepository;
    private final InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executor;

    public AssertionService(LlmReqConstructor llmReqConstructor,
                            MemoryAssertionRepository assertionRepository,
                            AssertionGroupRepository groupRepository,
                            AssertionLineageRepository lineageRepository,
                            InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore,
                            EmbeddingModel embeddingModel,
                            @Qualifier("embeddingExecutor") ExecutorService executor,
                            PromptLoader promptLoader) {
        this.llmReqConstructor = llmReqConstructor;
        this.assertionRepository = assertionRepository;
        this.groupRepository = groupRepository;
        this.lineageRepository = lineageRepository;
        this.assertionEmbeddingStore = assertionEmbeddingStore;
        this.embeddingModel = embeddingModel;
        this.executor = executor;

        if (promptLoader != null) {
            registerTasks(promptLoader);
        }
    }

    private static final String USER_DELIMITER = "---USER---";

    private void registerTasks(PromptLoader promptLoader) {
        // EXTRACT_TOPICS — with 3 few-shot example pairs: grouping, separation, empty
        String[] topicsParts = promptLoader.load("assertion/extract-topics.txt").split(USER_DELIMITER, 2);
        String topicsSystem = topicsParts[0].stripTrailing();
        String topicsUser = topicsParts.length > 1 ? topicsParts[1].strip() : "{messages}";
        List<ChatMessage> topicsExamples = List.of(
                // Example 1: same error type across turns → merged into ONE topic
                UserMessage.from("<turn role=\"user\">Yesterday I go to the park.</turn>\n" +
                        "<turn role=\"assistant\">Ah, you went to the park? Was it crowded?</turn>\n" +
                        "<turn role=\"user\">No, but I forget my key there.</turn>\n" +
                        "<turn role=\"assistant\">Oh no, you forgot your key? Hope you got it back.</turn>"),
                AiMessage.from("[\"past tense\"]"),
                // Example 2: different error types → SEPARATE topics
                UserMessage.from("<turn role=\"user\">I have a idea for the meeting.</turn>\n" +
                        "<turn role=\"assistant\">An idea? What is it?</turn>\n" +
                        "<turn role=\"user\">She always come late on Monday.</turn>\n" +
                        "<turn role=\"assistant\">She comes late? That's annoying.</turn>"),
                AiMessage.from("[\"articles\", \"third person -s\"]"),
                // Example 3: no grammar errors → EMPTY array
                UserMessage.from("<turn role=\"user\">I had a great weekend. Watched a movie and relaxed.</turn>\n" +
                        "<turn role=\"assistant\">Sounds lovely! What did you watch?</turn>\n" +
                        "<turn role=\"user\">An old comedy. Nothing special but fun.</turn>"),
                AiMessage.from("[]")
        );
        llmReqConstructor.register(TaskName.EXTRACT_TOPICS, LlmTaskDefinition
                .<ExtractTopicsParams, List<String>>builder()
                .systemTemplate(topicsSystem)
                .userTemplate(topicsUser)
                .exampleMessages(topicsExamples)
                .paramBuilder(p -> Map.of(
                        "groupName", p.groupName(),
                        "groupDescription", p.groupDescription(),
                        "messages", p.messages()))
                .parser(AssertionService::parseTopicList)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        // EXTRACT_STATE
        String[] stateParts = promptLoader.load("assertion/extract-state.txt").split(USER_DELIMITER, 2);
        String stateSystem = stateParts[0].stripTrailing();
        String stateUser = stateParts.length > 1 ? stateParts[1].strip() : "{groupName}: {topic}\n{messages}";
        llmReqConstructor.register(TaskName.EXTRACT_STATE, LlmTaskDefinition
                .<ExtractStateParams, String>builder()
                .systemTemplate(stateSystem)
                .userTemplate(stateUser)
                .paramBuilder(p -> Map.of(
                        "groupName", p.groupName(),
                        "topic", p.topic(),
                        "messages", p.messages()))
                .parser(String::trim)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        // JUDGE_SAME — with 3 few-shot example pairs
        String[] judgeParts = promptLoader.load("assertion/judge-same.txt").split(USER_DELIMITER, 2);
        String judgeSystem = judgeParts[0].stripTrailing();
        String judgeUser = judgeParts.length > 1 ? judgeParts[1].strip() : "Statement A: {newState}\nStatement B: {oldState}";
        List<ChatMessage> judgeExamples = List.of(
                UserMessage.from("Statement A: The user often forgets to use past tense when talking about yesterday's events.\n" +
                        "Statement B: The learner struggles with irregular past tense forms in conversation."),
                AiMessage.from("YES"),
                UserMessage.from("Statement A: The user makes subject-verb agreement errors with third person singular.\n" +
                        "Statement B: The user sometimes confuses \"a\" and \"an\" before vowel sounds."),
                AiMessage.from("NO"),
                UserMessage.from("Statement A: The user is aware of their past tense mistakes and self-corrects about half the time.\n" +
                        "Statement B: The user still makes past tense errors but occasionally self-corrects."),
                AiMessage.from("YES")
        );
        llmReqConstructor.register(TaskName.JUDGE_SAME, LlmTaskDefinition
                .<JudgeParams, Boolean>builder()
                .systemTemplate(judgeSystem)
                .userTemplate(judgeUser)
                .exampleMessages(judgeExamples)
                .paramBuilder(p -> Map.of(
                        "newState", p.newState(),
                        "oldState", p.oldState()))
                .parser(s -> "YES".equalsIgnoreCase(s.trim()))
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        // MERGE_ASSERTION
        String[] mergeParts = promptLoader.load("assertion/merge-assertion.txt").split(USER_DELIMITER, 2);
        String mergeSystem = mergeParts[0].stripTrailing();
        String mergeUser = mergeParts.length > 1 ? mergeParts[1].strip() : "Statement A: {stateA}\nStatement B: {stateB}";
        llmReqConstructor.register(TaskName.MERGE_ASSERTION, LlmTaskDefinition
                .<MergeParams, String>builder()
                .systemTemplate(mergeSystem)
                .userTemplate(mergeUser)
                .paramBuilder(p -> Map.of(
                        "stateA", p.stateA(),
                        "stateB", p.stateB()))
                .parser(String::trim)
                .errorStrategy(ErrorStrategy.THROW)
                .build());
    }

    // ── Public API ──────────────────────────────────────────────

    public CompletableFuture<Void> generateAssertionsAsync(String sessionId, String userId,
                                                            AgentMode mode, List<List<MessageData>> segments) {
        long startTime = System.currentTimeMillis();
        TaskContext ctx = new TaskContext(sessionId, userId, mode.name());

        return CompletableFuture.supplyAsync(() -> {
            AssertionGroup group = groupRepository.findByName("error-pattern")
                    .orElseThrow(() -> new IllegalStateException("AssertionGroup 'error-pattern' not found"));
            return extract(sessionId, userId, mode, segments, group, ctx);
        }, executor).thenAccept(newAssertions -> {
            long extractElapsed = System.currentTimeMillis() - startTime;
            log.info("AssertionService: extract done in {}ms, {} assertions", extractElapsed, newAssertions.size());

            if (!newAssertions.isEmpty()) {
                manage(sessionId, userId, mode, newAssertions, ctx);
                long totalElapsed = System.currentTimeMillis() - startTime;
                log.info("AssertionService: pipeline done in {}ms", totalElapsed);
            }
        });
    }

    List<MemoryAssertion> extract(String sessionId, String userId, AgentMode mode,
                                   List<List<MessageData>> segments, AssertionGroup group) {
        return extract(sessionId, userId, mode, segments, group, null);
    }

    // ── Package-private for testing ─────────────────────────────

    List<MemoryAssertion> extract(String sessionId, String userId, AgentMode mode,
                                   List<List<MessageData>> segments, AssertionGroup group, TaskContext ctx) {
        if (ctx == null) {
            ctx = new TaskContext(sessionId, userId, mode.name());
        }

        if (segments.isEmpty() || (segments.size() == 1 && segments.get(0).isEmpty())) {
            return Collections.emptyList();
        }

        List<MemoryAssertion> allAssertions = new ArrayList<>();

        for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
            List<MessageData> segment = segments.get(segIdx);
            if (segment.isEmpty()) continue;

            String labeledMessages = SessionComplete.buildLabeledMessages(segment);

            long t1 = System.currentTimeMillis();
            List<String> topics = extractTopicsWithRetry(
                    new ExtractTopicsParams(group.getName(), group.getDescription(), labeledMessages), ctx);
            log.info("AssertionService: Step1 (topics) segment {} done in {}ms, {} topics",
                    segIdx, System.currentTimeMillis() - t1, topics.size());

            for (String topic : topics) {
                long t2 = System.currentTimeMillis();
                String state = llmReqConstructor.execute(TaskName.EXTRACT_STATE,
                        new ExtractStateParams(group.getName(), topic, labeledMessages), ctx);
                log.info("AssertionService: Step2 (state) topic='{}' done in {}ms",
                        topic, System.currentTimeMillis() - t2);

                MemoryAssertion assertion = new MemoryAssertion(group, sessionId, userId, mode, topic, state);
                assertion = assertionRepository.save(assertion);
                indexAsync(assertion.getId(), assertion.getState());
                allAssertions.add(assertion);
            }
        }

        log.info("AssertionService: extract total {} assertions", allAssertions.size());
        return allAssertions;
    }

    void manage(String sessionId, String userId, AgentMode mode,
                List<MemoryAssertion> newAssertions, TaskContext ctx) {
        long t0 = System.currentTimeMillis();
        int mergeCount = 0;

        for (MemoryAssertion newAssertion : newAssertions) {
            // Search top-3 similar old assertions
            Embedding queryEmbedding = embeddingModel.embed(newAssertion.getState()).content();
            List<TextSegment> candidates = assertionEmbeddingStore.search(
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .minScore(0.5)
                            .build()).matches().stream()
                    .map(m -> m.embedded())
                    .toList();

            for (TextSegment candidate : candidates) {
                String oldId = candidate.metadata().getString("assertionId");
                if (oldId == null || oldId.equals(newAssertion.getId())) continue;

                MemoryAssertion oldAssertion = assertionRepository.findById(oldId).orElse(null);
                if (oldAssertion == null || !oldAssertion.isEnabled()) continue;
                if (!oldAssertion.getUserId().equals(userId) || oldAssertion.getMode() != mode) continue;
                if (oldAssertion.getSessionId().equals(sessionId)) continue;

                // Judge
                boolean isSame = llmReqConstructor.execute(TaskName.JUDGE_SAME,
                        new JudgeParams(newAssertion.getState(), oldAssertion.getState()), ctx);

                if (isSame) {
                    // Merge
                    String mergedState = llmReqConstructor.execute(TaskName.MERGE_ASSERTION,
                            new MergeParams(newAssertion.getState(), oldAssertion.getState()), ctx);

                    // Soft-delete old
                    oldAssertion.setEnabled(false);
                    assertionRepository.save(oldAssertion);
                    removeFromStore(oldId);

                    // Insert merged
                    MemoryAssertion merged = new MemoryAssertion(
                            newAssertion.getGroup(), sessionId, userId, mode,
                            newAssertion.getTopic(), mergedState);
                    merged = assertionRepository.save(merged);
                    indexAsync(merged.getId(), merged.getState());

                    // Record lineage: old → merged
                    lineageRepository.save(new AssertionLineage(oldId, merged.getId(), "MERGE"));
                    mergeCount++;

                    log.debug("AssertionService: merged assertion {} with {}, new={}",
                            oldId, newAssertion.getId(), merged.getId());
                }
            }
        }

        log.info("AssertionService: manage done in {}ms, {} merges",
                System.currentTimeMillis() - t0, mergeCount);
    }

    // ── Internal helpers ────────────────────────────────────────

    private void indexAsync(String assertionId, String state) {
        CompletableFuture.runAsync(() -> {
            try {
                Embedding embedding = embeddingModel.embed(state).content();
                dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
                metadata.add("assertionId", assertionId);
                TextSegment segment = TextSegment.from(state, metadata);
                assertionEmbeddingStore.add(embedding, segment);
            } catch (Exception e) {
                log.warn("AssertionService: index failed for assertion {}: {}", assertionId, e.getMessage());
            }
        }, executor);
    }

    private void removeFromStore(String assertionId) {
        // V1: rely on enabled=false in DB as authoritative; embedding store entries
        // are filtered during search by checking isEnabled() in manage().
        // Full store removal requires InMemoryEmbeddingStore.removeAll(Filter)
        // which may not be available in langchain4j 1.0.0-beta1.
    }

    private static final int MAX_TOPIC_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    private List<String> extractTopicsWithRetry(ExtractTopicsParams params, TaskContext ctx) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_TOPIC_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                String rawResponse = llmReqConstructor.executeRaw(TaskName.EXTRACT_TOPICS, params, ctx);
                if (rawResponse == null || rawResponse.isBlank()) {
                    lastException = new RuntimeException("Empty response from LLM");
                    log.warn("AssertionService: extractTopics attempt {} returned empty, retrying", attempt + 1);
                    continue;
                }
                List<String> topics = parseTopicList(rawResponse);
                // parseTopicList returns emptyList on parse failure —
                // distinguish genuine [] from parse failure by checking the raw response
                if (topics.isEmpty() && !rawResponse.trim().equals("[]")) {
                    lastException = new RuntimeException("Failed to parse topic list");
                    log.warn("AssertionService: extractTopics attempt {} parse failed, raw={}", attempt + 1, rawResponse);
                    continue;
                }
                return topics;
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("AssertionService: extractTopics attempt {} threw: {}", attempt + 1, e.getMessage());
                // Continue to retry on runtime exceptions too
            }
        }
        log.warn("AssertionService: extractTopics all {} attempts exhausted, returning empty", MAX_TOPIC_RETRIES + 1);
        return Collections.emptyList();
    }

    private static List<String> parseTopicList(String response) {
        try {
            return mapper.readValue(response.trim(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse topic list JSON: {}", response, e);
            return Collections.emptyList();
        }
    }

    // ── Parameter records ───────────────────────────────────────

    record ExtractTopicsParams(String groupName, String groupDescription, String messages) {}
    record ExtractStateParams(String groupName, String topic, String messages) {}
    record JudgeParams(String newState, String oldState) {}
    record MergeParams(String stateA, String stateB) {}
}
