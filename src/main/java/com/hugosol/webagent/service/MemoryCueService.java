package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.MemoryCueAgent;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import com.hugosol.webagent.repository.MemoryCueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class MemoryCueService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCueService.class);
    private static final Object consolidationLock = new Object();

    private final MemoryCueAgent agent;
    private final MemoryCueRepository repository;
    private final ExecutorService executor;

    public MemoryCueService(MemoryCueAgent agent,
                            MemoryCueRepository repository,
                            @Qualifier("memoryExecutor") ExecutorService executor) {
        this.agent = agent;
        this.repository = repository;
        this.executor = executor;
    }

    public CompletableFuture<Void> generateCuesAsync(String sessionId, String userId, AgentMode mode, List<MessageData> messages) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            List<Integer> switchPoints;
            try {
                switchPoints = agent.detectSwitches(messages, mode);
            } catch (Exception e) {
                log.warn("MemoryCueService: detectSwitches failed for session {}: {}", sessionId, e.getMessage());
                repository.save(new MemoryCue(sessionId, userId, mode, -1,
                        null, null, Collections.emptyList(), MemoryCueStatus.FIRST_CALL_FAILED));
                return Collections.<CompletableFuture<Void>>emptyList();
            }

            List<List<MessageData>> segments = splitBySwitches(messages, switchPoints);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < segments.size(); i++) {
                final int segmentIndex = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        MemoryCueAgent.CueResult result = agent.generateCue(segments.get(segmentIndex), mode, segmentIndex);
                        repository.save(new MemoryCue(sessionId, userId, mode, segmentIndex,
                                result.topic(), result.summary(), result.tags(),
                                MemoryCueStatus.COMPLETED));
                    } catch (Exception e) {
                        log.warn("MemoryCueService: generateCue failed for session {} segment {}: {}",
                                sessionId, segmentIndex, e.getMessage());
                        repository.save(new MemoryCue(sessionId, userId, mode, segmentIndex,
                                null, null, Collections.emptyList(),
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
                        try {
                            consolidateTags(sessionId, userId, mode);
                        } finally {
                            long elapsed = System.currentTimeMillis() - triggerTime;
                            log.info("Background task duration (MemoryCue + consolidation): {}ms", elapsed);
                        }
                    }, executor);
        });
    }

    private void consolidateTags(String sessionId, String userId, AgentMode mode) {
        synchronized (consolidationLock) {
            List<MemoryCue> sessionCues = repository.findBySessionId(sessionId);

            boolean hasSegmentFailed = sessionCues.stream()
                    .anyMatch(c -> c.getStatus() == MemoryCueStatus.SEGMENT_FAILED);
            if (hasSegmentFailed) {
                log.warn("MemoryCueService: skipping tag consolidation for session {} due to SEGMENT_FAILED", sessionId);
                return;
            }

            boolean hasFirstCallFailed = sessionCues.stream()
                    .anyMatch(c -> c.getStatus() == MemoryCueStatus.FIRST_CALL_FAILED);
            if (hasFirstCallFailed) {
                log.warn("MemoryCueService: skipping tag consolidation for session {} due to FIRST_CALL_FAILED", sessionId);
                return;
            }

            List<MemoryCue> allCues = repository.findByUserIdAndMode(userId, mode);
            Map<String, Integer> frequencyMap = new HashMap<>();
            for (MemoryCue cue : allCues) {
                if (cue.getStatus() != MemoryCueStatus.COMPLETED) continue;
                for (String tag : cue.getTags()) {
                    frequencyMap.merge(tag, 1, Integer::sum);
                }
            }

            try {
                Map<String, String> mapping = agent.consolidateTags(frequencyMap);
                log.info("MemoryCueService: tag consolidation mapping for session {}: {}", sessionId, mapping);

                for (MemoryCue cue : sessionCues) {
                    if (cue.getStatus() != MemoryCueStatus.COMPLETED) continue;

                    List<String> originalTags = new ArrayList<>(cue.getTags());
                    List<String> newTags = new ArrayList<>();
                    for (String tag : originalTags) {
                        String canonical = mapping.getOrDefault(tag, tag);
                        if (!newTags.contains(canonical)) {
                            newTags.add(canonical);
                        }
                    }

                    if (!newTags.equals(originalTags)) {
                        cue.setTags(newTags);
                        repository.save(cue);
                        log.info("MemoryCueService: consolidated tags for cue {} from {} to {}",
                                cue.getId(), originalTags, newTags);
                    }
                }
            } catch (Exception e) {
                log.warn("MemoryCueService: tag consolidation failed for session {}: {}", sessionId, e.getMessage());
            }
        }
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
