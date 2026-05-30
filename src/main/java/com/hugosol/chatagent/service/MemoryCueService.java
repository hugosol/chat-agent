package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.MemoryCueAgent;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryCue;
import com.hugosol.chatagent.model.MemoryCueStatus;
import com.hugosol.chatagent.repository.MemoryCueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class MemoryCueService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCueService.class);

    private final MemoryCueAgent agent;
    private final MemoryCueRepository repository;
    private final EmbeddingService embeddingService;
    private final ExecutorService executor;

    public MemoryCueService(MemoryCueAgent agent,
                            MemoryCueRepository repository,
                            EmbeddingService embeddingService,
                            @Qualifier("llmRequestExecutor") ExecutorService executor) {
        this.agent = agent;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.executor = executor;
    }

    public CompletableFuture<Void> generateCuesAsync(String sessionId, String userId, AgentMode mode, List<MessageData> messages) {
        long startTime = System.currentTimeMillis();
        TaskContext ctx = new TaskContext(sessionId, userId, mode.name());

        return CompletableFuture.supplyAsync(() -> {
            List<Integer> switchPoints;
            try {
                switchPoints = agent.detectSwitches(messages, mode, ctx);
            } catch (Exception e) {
                log.warn("MemoryCueService: detectSwitches failed for session {}: {}", sessionId, e.getMessage());
                repository.save(new MemoryCue(sessionId, userId, mode, -1,
                        null, null, MemoryCueStatus.FIRST_CALL_FAILED));
                return Collections.<CompletableFuture<Void>>emptyList();
            }

            List<List<MessageData>> segments = splitBySwitches(messages, switchPoints);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < segments.size(); i++) {
                final int segmentIndex = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        MemoryCueAgent.CueResult result = agent.generateCue(segments.get(segmentIndex), mode, segmentIndex, ctx);
                        MemoryCue cue = repository.save(new MemoryCue(sessionId, userId, mode, segmentIndex,
                                result.topic(), result.summary(),
                                MemoryCueStatus.COMPLETED));
                        embeddingService.indexAsync(cue.getId(), result.topic(), result.summary(), mode, userId, cue.getCreateTime());
                        log.debug("MemoryCueService: dispatched indexAsync for cue {}", cue.getId());
                    } catch (Exception e) {
                        log.warn("MemoryCueService: generateCue failed for session {} segment {}: {}",
                                sessionId, segmentIndex, e.getMessage());
                        repository.save(new MemoryCue(sessionId, userId, mode, segmentIndex,
                                null, null,
                                MemoryCueStatus.SEGMENT_FAILED));
                    }
                }, executor));
            }

            log.info("MemoryCueService: dispatched {} cue segments for session {}", segments.size(), sessionId);
            return futures;
        }, executor).thenCompose(futures -> {
            if (futures.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            long triggerTime = startTime;
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRunAsync(() -> {
                        long elapsed = System.currentTimeMillis() - triggerTime;
                        log.info("Background task duration (MemoryCue): {}s", String.format("%.1f", elapsed / 1000.0));
                    }, executor);
        });
    }

    private static List<List<MessageData>> splitBySwitches(List<MessageData> messages, List<Integer> switchPoints) {
        if (switchPoints.isEmpty()) {
            return List.of(messages);
        }

        List<List<MessageData>> segments = new ArrayList<>();
        int startIdx = 0;
        for (int switchIdx : switchPoints) {
            int endIdx = Math.min(switchIdx + 1, messages.size());
            if (startIdx < messages.size()) {
                segments.add(messages.subList(startIdx, endIdx));
            }
            startIdx = endIdx;
        }
        if (startIdx < messages.size()) {
            segments.add(messages.subList(startIdx, messages.size()));
        }
        return segments;
    }
}
