package com.hugosol.webagent.service;

import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.dto.CueMatch;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import com.hugosol.webagent.repository.MemoryCueRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private InMemoryEmbeddingStore<TextSegment> store;
    private final MemoryCueRepository memoryCueRepository;
    private final AppProperties appProperties;
    private final ExecutorService executor;

    @Autowired
    public EmbeddingService(EmbeddingModel embeddingModel,
                            MemoryCueRepository memoryCueRepository,
                            AppProperties appProperties,
                            @Qualifier("embeddingExecutor") ExecutorService executor) {
        this(embeddingModel, new InMemoryEmbeddingStore<>(), memoryCueRepository, appProperties, executor);
    }

    EmbeddingService(EmbeddingModel embeddingModel,
                     InMemoryEmbeddingStore<TextSegment> store,
                     MemoryCueRepository memoryCueRepository,
                     AppProperties appProperties,
                     ExecutorService executor) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.memoryCueRepository = memoryCueRepository;
        this.appProperties = appProperties;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        Path filePath = getStorePath();
        if (Files.exists(filePath)) {
            try {
                store = InMemoryEmbeddingStore.fromFile(filePath);
                log.info("EmbeddingService: loaded store from disk");
                return;
            } catch (Exception e) {
                log.warn("EmbeddingService: failed to load store from disk, rebuilding from H2: {}", e.getMessage());
            }
        } else {
            log.info("EmbeddingService: no disk store at {}, building from H2", filePath);
        }

        rebuildFromH2();
    }

    private void rebuildFromH2() {
        List<MemoryCue> completedCues = memoryCueRepository.findAllByStatus(MemoryCueStatus.COMPLETED);
        log.info("EmbeddingService: building store from {} H2 records", completedCues.size());
        for (MemoryCue cue : completedCues) {
            try {
                indexSync(cue.getId(), cue.getTopic(), cue.getSummary(), cue.getMode(), cue.getUserId());
            } catch (Exception e) {
                log.warn("EmbeddingService: failed to embed cue {} during rebuild: {}", cue.getId(), e.getMessage());
            }
        }
        log.info("EmbeddingService: store built from H2");
    }

    public CompletableFuture<Void> indexAsync(String cueId, String topic, String summary,
                                               AgentMode mode, String userId) {
        return CompletableFuture.runAsync(() -> {
            try {
                indexSync(cueId, topic, summary, mode, userId);
                saveToDiskAsync();
                log.info("EmbeddingService: indexAsync completed for cue {}", cueId);
            } catch (Exception e) {
                log.warn("EmbeddingService: indexAsync failed for cue {}: {}", cueId, e.getMessage());
            }
        }, executor);
    }

    private void indexSync(String cueId, String topic, String summary, AgentMode mode, String userId) {
        String text = (topic != null ? topic : "") + " " + (summary != null ? summary : "");
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = TextSegment.from(text);
        segment.metadata().add("cueId", cueId);
        segment.metadata().add("topic", topic != null ? topic : "");
        segment.metadata().add("mode", mode.name());
        segment.metadata().add("userId", userId);
        store.add(embedding, segment);
        log.info("EmbeddingService: indexed cue {} topic={} mode={} userId={}", cueId, topic, mode, userId);
    }

    public List<CueMatch> search(String userInput, AgentMode mode, String userId,
                                  int topK, double threshold) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(userInput).content();

            Filter filter = new And(
                    new IsEqualTo("mode", mode.name()),
                    new IsEqualTo("userId", userId)
            );

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .minScore(threshold)
                    .filter(filter)
                    .build();

            EmbeddingSearchResult<TextSegment> result = store.search(request);

            List<CueMatch> matches = new ArrayList<>();
            for (var match : result.matches()) {
                if (match.score() >= threshold) {
                    matches.add(new CueMatch(
                            match.embedded().metadata().getString("cueId"),
                            match.embedded().metadata().getString("topic"),
                            match.embedded().metadata().getString("cueId") != null
                                    ? match.embedded().text() : match.embedded().metadata().getString("topic"),
                            match.score()
                    ));
                }
            }

            log.info("EmbeddingService: search returned {} results for mode={} userId={} (topK={} threshold={}): {}",
                    matches.size(), mode.name(), userId, topK, threshold, formatScores(matches));
            if (matches.isEmpty()) {
                log.info("EmbeddingService: no matches above threshold {} for mode={} userId={}", threshold, mode, userId);
            }

            return matches;
        } catch (Exception e) {
            log.warn("EmbeddingService: search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void saveToDiskAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                saveToDisk();
                log.info("EmbeddingService: async save to disk completed");
            } catch (Exception e) {
                log.warn("EmbeddingService: async save to disk failed: {}", e.getMessage());
            }
        }, executor);
    }

    @PreDestroy
    public void saveToDisk() {
        Path filePath = getStorePath();
        try {
            Files.createDirectories(filePath.getParent());
            store.serializeToFile(filePath);
            log.info("EmbeddingService: store saved to disk");
        } catch (IOException e) {
            log.error("EmbeddingService: failed to save store to disk", e);
            throw new RuntimeException("Failed to save embedding store to disk", e);
        }
    }

    protected Path getStorePath() {
        return Paths.get(appProperties.getMemory().getStorePath());
    }

    InMemoryEmbeddingStore<TextSegment> getStore() {
        return store;
    }

    private static String formatScores(List<CueMatch> matches) {
        return matches.stream()
                .map(m -> "topic:" + m.topic() + ", score:" + String.format("%.2f", m.score()) + ", cueId:" + m.cueId())
                .collect(Collectors.joining(" | ", "[", "]"));
    }
}
