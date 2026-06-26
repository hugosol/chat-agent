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

    private final TaskRunner runner;
    private final MemoryAssertionRepository assertionRepository;
    private final AssertionGroupRepository groupRepository;
    private final AssertionLineageRepository lineageRepository;
    private final InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executor;

    public AssertionService(TaskRunner runner,
                            MemoryAssertionRepository assertionRepository,
                            AssertionGroupRepository groupRepository,
                            AssertionLineageRepository lineageRepository,
                            InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore,
                            EmbeddingModel embeddingModel,
                            @Qualifier("embeddingExecutor") ExecutorService executor,
                            PromptLoader promptLoader) {
        this.runner = runner;
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

    private void registerTasks(PromptLoader promptLoader) {
        String topicsTemplate = promptLoader.load("assertion/extract-topics.txt");
        runner.register(TaskName.EXTRACT_TOPICS, TaskDefinition
                .<ExtractTopicsParams, List<String>>builder()
                .template(topicsTemplate)
                .paramBuilder(p -> Map.of(
                        "groupName", p.groupName(),
                        "groupDescription", p.groupDescription(),
                        "messages", p.messages()))
                .parser(AssertionService::parseTopicList)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        String stateTemplate = promptLoader.load("assertion/extract-state.txt");
        runner.register(TaskName.EXTRACT_STATE, TaskDefinition
                .<ExtractStateParams, String>builder()
                .template(stateTemplate)
                .paramBuilder(p -> Map.of(
                        "groupName", p.groupName(),
                        "topic", p.topic(),
                        "messages", p.messages()))
                .parser(String::trim)
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        String judgeTemplate = promptLoader.load("assertion/judge-same.txt");
        runner.register(TaskName.JUDGE_SAME, TaskDefinition
                .<JudgeParams, Boolean>builder()
                .template(judgeTemplate)
                .paramBuilder(p -> Map.of(
                        "newState", p.newState(),
                        "oldState", p.oldState()))
                .parser(s -> "YES".equalsIgnoreCase(s.trim()))
                .errorStrategy(ErrorStrategy.THROW)
                .build());

        String mergeTemplate = promptLoader.load("assertion/merge-assertion.txt");
        runner.register(TaskName.MERGE_ASSERTION, TaskDefinition
                .<MergeParams, String>builder()
                .template(mergeTemplate)
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
                String state = runner.requestModel(TaskName.EXTRACT_STATE,
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
                boolean isSame = runner.requestModel(TaskName.JUDGE_SAME,
                        new JudgeParams(newAssertion.getState(), oldAssertion.getState()), ctx);

                if (isSame) {
                    // Merge
                    String mergedState = runner.requestModel(TaskName.MERGE_ASSERTION,
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
                String rawResponse = runner.requestRaw(TaskName.EXTRACT_TOPICS, params, ctx);
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
