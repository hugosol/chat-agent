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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MemoryCueServiceTest {

    private MemoryCueAgent agent;
    private MemoryCueRepository repository;
    private ExecutorService executor;
    private MemoryCueService service;

    @BeforeEach
    void setUp() {
        agent = mock(MemoryCueAgent.class);
        repository = mock(MemoryCueRepository.class);
        executor = Executors.newSingleThreadExecutor();
        service = new MemoryCueService(agent, repository, executor);
    }

    @Test
    void noSwitch_writesOneCompletedRecord() throws Exception {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of());
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Travel", "summary", List.of("travel")));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi")));
        Thread.sleep(200);

        ArgumentCaptor<MemoryCue> captor = ArgumentCaptor.forClass(MemoryCue.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MemoryCueStatus.COMPLETED);
        assertThat(captor.getValue().getSegmentIndex()).isEqualTo(0);
        assertThat(captor.getValue().getTopic()).isEqualTo("Travel");
    }

    @Test
    void withSwitch_writesMultipleCompletedRecords() throws Exception {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of(2));
        when(agent.generateCue(any(), any(), anyInt()))
                .thenReturn(new MemoryCueAgent.CueResult("Topic1", "sum1", List.of("a")))
                .thenReturn(new MemoryCueAgent.CueResult("Topic2", "sum2", List.of("b")));

        List<MessageData> messages = List.of(
                msg("msg0"), msg("msg1"), msg("msg2"), msg("msg3"), msg("msg4"));
        service.generateCuesAsync("s1", "user-1", AgentMode.DAILY_TALK, messages);
        Thread.sleep(200);

        verify(agent).detectSwitches(any(), eq(AgentMode.DAILY_TALK));
        verify(agent, times(2)).generateCue(any(), any(), anyInt());
        verify(repository, times(2)).save(any(MemoryCue.class));
    }

    @Test
    void firstCallFailed_writesFailureRecord() throws Exception {
        when(agent.detectSwitches(any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, List.of(msg("Hi")));
        Thread.sleep(200);

        ArgumentCaptor<MemoryCue> captor = ArgumentCaptor.forClass(MemoryCue.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MemoryCueStatus.FIRST_CALL_FAILED);
        assertThat(captor.getValue().getSegmentIndex()).isEqualTo(-1);
        verify(agent, never()).generateCue(any(), any(), anyInt());
    }

    @Test
    void segmentGenerateFails_writesPartialCompletedAndFailed() throws Exception {
        when(agent.detectSwitches(any(), any())).thenReturn(List.of(1));
        when(agent.generateCue(any(), any(), eq(0)))
                .thenReturn(new MemoryCueAgent.CueResult("OK", "sum", List.of("ok")));
        when(agent.generateCue(any(), any(), eq(1)))
                .thenThrow(new RuntimeException("parse error"));

        List<MessageData> messages = List.of(msg("a"), msg("b"), msg("c"));
        service.generateCuesAsync("s1", "user-1", AgentMode.WORKPLACE_STANDUP, messages);
        Thread.sleep(500);

        ArgumentCaptor<MemoryCue> captor = ArgumentCaptor.forClass(MemoryCue.class);
        verify(repository, times(2)).save(captor.capture());

        List<MemoryCue> saved = captor.getAllValues();
        assertThat(saved).extracting(MemoryCue::getStatus)
                .containsExactlyInAnyOrder(MemoryCueStatus.COMPLETED, MemoryCueStatus.SEGMENT_FAILED);
    }

    private static MessageData msg(String text) {
        return new MessageData(MessageRole.USER, text, 0);
    }
}
