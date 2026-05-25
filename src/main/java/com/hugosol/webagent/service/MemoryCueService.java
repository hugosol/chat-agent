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
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class MemoryCueService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCueService.class);

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

    public void generateCuesAsync(String sessionId, String userId, AgentMode mode, List<MessageData> messages) {
        executor.execute(() -> {
            List<Integer> switchPoints;
            try {
                switchPoints = agent.detectSwitches(messages, mode);
            } catch (Exception e) {
                log.warn("MemoryCueService: detectSwitches failed for session {}: {}", sessionId, e.getMessage());
                repository.save(new MemoryCue(sessionId, userId, mode, -1,
                        null, null, Collections.emptyList(), MemoryCueStatus.FIRST_CALL_FAILED));
                return;
            }

            List<List<MessageData>> segments = splitBySwitches(messages, switchPoints);

            for (int i = 0; i < segments.size(); i++) {
                final int segmentIndex = i;
                executor.execute(() -> {
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
                });
            }

            log.info("MemoryCueService: dispatched {} cue segments for session {}", segments.size(), sessionId);
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
