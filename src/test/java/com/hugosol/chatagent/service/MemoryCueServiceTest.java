package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.MemoryCueAgent;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryCue;
import com.hugosol.chatagent.model.MemoryCueStatus;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.repository.MemoryCueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryCueServiceTest {

    private MemoryCueAgent agent;
    private MemoryCueRepository repository;
    private EmbeddingService embeddingService;
    private ExecutorService executor;
    private MemoryCueService service;

    private final List<MemoryCue> savedCues = new ArrayList<>();

    @BeforeEach
    void setUp() {
        agent = mock(MemoryCueAgent.class);
        repository = mock(MemoryCueRepository.class);
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.indexAsync(any(), anyString(), anyString(), any(), anyString(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(null));
        executor = Executors.newSingleThreadExecutor();
        service = new MemoryCueService(agent, repository, embeddingService, executor);
        savedCues.clear();

        doAnswer(inv -> {
            MemoryCue cue = inv.getArgument(0);
            if (cue.getId() == null) {
                cue.setId("generated-" + System.nanoTime());
            }
            savedCues.add(cue);
            return cue;
        }).when(repository).save(any(MemoryCue.class));
    }

    @Test
    void noSwitch_writesOneCompletedRecordAndIndexes() {
        when(agent.detectSwitches(any(), any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt(), any()))
                .thenReturn(new MemoryCueAgent.CueResult("Travel", "summary"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi"))).join();

        assertThat(savedCues).hasSize(1);
        assertThat(savedCues.get(0).getStatus()).isEqualTo(MemoryCueStatus.COMPLETED);
        assertThat(savedCues.get(0).getSegmentIndex()).isEqualTo(0);
        assertThat(savedCues.get(0).getTopic()).isEqualTo("Travel");

        verify(embeddingService).indexAsync(any(), eq("Travel"), eq("summary"),
                eq(AgentMode.WORKPLACE_STANDUP), eq("user-1"), isNull());
    }

    @Test
    void withSwitch_writesMultipleCompletedRecordsAndIndexesBoth() {
        when(agent.detectSwitches(any(), any(), any())).thenReturn(List.of(2));
        when(agent.generateCue(any(), any(), anyInt(), any()))
                .thenReturn(new MemoryCueAgent.CueResult("Topic1", "sum1"))
                .thenReturn(new MemoryCueAgent.CueResult("Topic2", "sum2"));

        List<MessageData> messages = List.of(
                msg("msg0"), msg("msg1"), msg("msg2"), msg("msg3"), msg("msg4"));
        service.generateCuesAsync("s1", "user-1", AgentMode.DAILY_TALK, messages).join();

        verify(agent).detectSwitches(any(), eq(AgentMode.DAILY_TALK), any());
        verify(agent, times(2)).generateCue(any(), any(), anyInt(), any());
        assertThat(savedCues).hasSize(2);

        verify(embeddingService, times(2)).indexAsync(any(), anyString(), anyString(),
                any(), anyString(), isNull());
    }

    @Test
    void firstCallFailed_skipsIndexing() {
        when(agent.detectSwitches(any(), any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi"))).join();

        assertThat(savedCues).hasSize(1);
        assertThat(savedCues.get(0).getStatus()).isEqualTo(MemoryCueStatus.FIRST_CALL_FAILED);
        assertThat(savedCues.get(0).getSegmentIndex()).isEqualTo(-1);
        verify(agent, never()).generateCue(any(), any(), anyInt(), any());
        verify(embeddingService, never()).indexAsync(any(), anyString(), anyString(), any(), anyString(), isNull());
    }

    @Test
    void segmentGenerateFails_indexesOnlyCompleted() {
        when(agent.detectSwitches(any(), any(), any())).thenReturn(List.of(1));
        when(agent.generateCue(any(), any(), eq(0), any()))
                .thenReturn(new MemoryCueAgent.CueResult("OK", "sum"));
        when(agent.generateCue(any(), any(), eq(1), any()))
                .thenThrow(new RuntimeException("parse error"));

        List<MessageData> messages = List.of(msg("a"), msg("b"), msg("c"));
        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, messages).join();

        assertThat(savedCues).extracting(MemoryCue::getStatus)
                .containsExactlyInAnyOrder(MemoryCueStatus.COMPLETED, MemoryCueStatus.SEGMENT_FAILED);

        verify(embeddingService, times(1)).indexAsync(any(), anyString(), anyString(), any(), anyString(), isNull());
    }

    private static MessageData msg(String text) {
        return new MessageData(MessageRole.USER, text, 0);
    }
}
