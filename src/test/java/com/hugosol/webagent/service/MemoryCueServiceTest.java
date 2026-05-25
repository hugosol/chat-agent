package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.MemoryCueAgent;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.repository.MemoryCueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MemoryCueServiceTest {

    private MemoryCueAgent agent;
    private MemoryCueRepository repository;
    private ExecutorService executor;
    private MemoryCueService service;

    private final List<MemoryCue> savedCues = new ArrayList<>();

    @BeforeEach
    void setUp() {
        agent = mock(MemoryCueAgent.class);
        repository = mock(MemoryCueRepository.class);
        executor = Executors.newSingleThreadExecutor();
        service = new MemoryCueService(agent, repository, executor);
        savedCues.clear();

        doAnswer(inv -> {
            MemoryCue cue = inv.getArgument(0);
            savedCues.add(cue);
            return cue;
        }).when(repository).save(any(MemoryCue.class));
    }

    // ========== Existing tests (refactored with .join()) ==========

    @Test
    void noSwitch_writesOneCompletedRecord() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Travel", "summary", List.of("travel")));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi"))).join();

        assertThat(savedCues).hasSize(1);
        assertThat(savedCues.get(0).getStatus()).isEqualTo(MemoryCueStatus.COMPLETED);
        assertThat(savedCues.get(0).getSegmentIndex()).isEqualTo(0);
        assertThat(savedCues.get(0).getTopic()).isEqualTo("Travel");
    }

    @Test
    void withSwitch_writesMultipleCompletedRecords() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of(2));
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Topic1", "sum1", List.of("a")))
                .thenReturn(new MemoryCueAgent.CueResult("Topic2", "sum2", List.of("b")));

        List<MessageData> messages = List.of(
                msg("msg0"), msg("msg1"), msg("msg2"), msg("msg3"), msg("msg4"));
        service.generateCuesAsync("s1", "user-1", AgentMode.DAILY_TALK, messages).join();

        verify(agent).detectSwitches(any(), eq(AgentMode.DAILY_TALK));
        verify(agent, times(2)).generateCue(any(), any(), anyInt());
        assertThat(savedCues).hasSize(2);
    }

    @Test
    void firstCallFailed_writesFailureRecord() {
        when(agent.detectSwitches(any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi"))).join();

        assertThat(savedCues).hasSize(1);
        assertThat(savedCues.get(0).getStatus()).isEqualTo(MemoryCueStatus.FIRST_CALL_FAILED);
        assertThat(savedCues.get(0).getSegmentIndex()).isEqualTo(-1);
        verify(agent, never()).generateCue(any(), any(), anyInt());
    }

    @Test
    void segmentGenerateFails_writesPartialCompletedAndFailed() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of(1));
        when(agent.generateCue(any(), any(), eq(0)))
                .thenReturn(new MemoryCueAgent.CueResult("OK", "sum", List.of("ok")));
        when(agent.generateCue(any(), any(), eq(1)))
                .thenThrow(new RuntimeException("parse error"));

        List<MessageData> messages = List.of(msg("a"), msg("b"), msg("c"));
        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, messages).join();

        assertThat(savedCues).extracting(MemoryCue::getStatus)
                .containsExactlyInAnyOrder(MemoryCueStatus.COMPLETED, MemoryCueStatus.SEGMENT_FAILED);
    }

    // ========== Consolidation tests ==========

    @Test
    void consolidation_withMapping_replacesAndDeduplicates() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Pets", "summary",
                        List.of("spaniel", "dog", "travel")));

        var sessionCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Pets", "summary", new ArrayList<>(List.of("spaniel", "dog", "travel")),
                MemoryCueStatus.COMPLETED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(sessionCue));
        when(repository.findByUserIdAndMode("user-1", AgentMode.WORKPLACE_STANDUP))
                .thenReturn(List.of(sessionCue));

        when(agent.consolidateTags(anyMap()))
                .thenReturn(Map.of("spaniel", "dog"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP,
                List.of(msg("I have a spaniel"))).join();

        verify(agent).consolidateTags(anyMap());

        var updatedCues = savedCues.stream()
                .filter(c -> c.getTags() != null && c.getTags().equals(List.of("dog", "travel")))
                .toList();
        assertThat(updatedCues).as("should have consolidated tags: dog, travel").isNotEmpty();
    }

    @Test
    void consolidation_noChanges_skipsSave() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Work", "summary", List.of("work", "meeting")));

        var sessionCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Work", "summary", new ArrayList<>(List.of("work", "meeting")),
                MemoryCueStatus.COMPLETED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(sessionCue));
        when(repository.findByUserIdAndMode("user-1", AgentMode.WORKPLACE_STANDUP))
                .thenReturn(List.of(sessionCue));

        when(agent.consolidateTags(anyMap())).thenReturn(Collections.emptyMap());

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP,
                List.of(msg("work stuff"))).join();

        verify(agent).consolidateTags(anyMap());
        long consolidationSaves = savedCues.stream()
                .filter(c -> c.getStatus() == null
                        || c.getStatus() == MemoryCueStatus.COMPLETED)
                .count();
        assertThat(consolidationSaves).isEqualTo(1);
    }

    @Test
    void consolidation_segmentFailed_skips() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of(1));
        when(agent.generateCue(any(), any(), eq(0)))
                .thenReturn(new MemoryCueAgent.CueResult("OK", "sum", List.of("ok")));
        when(agent.generateCue(any(), any(), eq(1)))
                .thenThrow(new RuntimeException("parse error"));

        var completedCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "OK", "sum", List.of("ok"), MemoryCueStatus.COMPLETED);
        var failedCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 1,
                null, null, Collections.emptyList(), MemoryCueStatus.SEGMENT_FAILED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(completedCue, failedCue));

        List<MessageData> messages = List.of(msg("a"), msg("b"), msg("c"));
        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, messages).join();

        verify(agent, never()).consolidateTags(anyMap());
    }

    @Test
    void consolidation_firstCallFailed_skips() {
        when(agent.detectSwitches(any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        var failedCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, -1,
                null, null, Collections.emptyList(), MemoryCueStatus.FIRST_CALL_FAILED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(failedCue));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP,
                List.of(msg("Hi"))).join();

        verify(agent, never()).consolidateTags(anyMap());
    }

    @Test
    void consolidation_idempotent_noNewSaves() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Pets", "summary", List.of("dog", "travel")));

        var sessionCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Pets", "summary", new ArrayList<>(List.of("dog", "travel")),
                MemoryCueStatus.COMPLETED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(sessionCue));
        when(repository.findByUserIdAndMode("user-1", AgentMode.WORKPLACE_STANDUP))
                .thenReturn(List.of(sessionCue));

        when(agent.consolidateTags(anyMap())).thenReturn(Collections.emptyMap());

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP,
                List.of(msg("I have a dog"))).join();

        verify(agent).consolidateTags(anyMap());
        long consolidationSaves = savedCues.stream()
                .filter(c -> c.getStatus() == null
                        || c.getStatus() == MemoryCueStatus.COMPLETED)
                .count();
        assertThat(consolidationSaves).isEqualTo(1);
    }

    @Test
    void consolidation_agentError_swallowed() {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Work", "summary", List.of("work")));

        var sessionCue = new MemoryCue("s1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Work", "summary", new ArrayList<>(List.of("work")),
                MemoryCueStatus.COMPLETED);
        when(repository.findBySessionId("s1")).thenReturn(List.of(sessionCue));
        when(repository.findByUserIdAndMode("user-1", AgentMode.WORKPLACE_STANDUP))
                .thenReturn(List.of(sessionCue));

        when(agent.consolidateTags(anyMap())).thenThrow(new RuntimeException("LLM error"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP,
                List.of(msg("work stuff"))).join();

        verify(agent).consolidateTags(anyMap());
        long segmentSaves = savedCues.stream()
                .filter(c -> c.getStatus() == MemoryCueStatus.COMPLETED)
                .count();
        assertThat(segmentSaves).isEqualTo(1);
    }

    private static MessageData msg(String text) {
        return new MessageData(MessageRole.USER, text, 0);
    }
}
