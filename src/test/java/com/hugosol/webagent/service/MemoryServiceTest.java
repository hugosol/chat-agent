package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.MemoryAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryType;
import com.hugosol.webagent.model.UserMemory;
import com.hugosol.webagent.repository.UserMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    private MemoryAgent memoryAgent;
    private UserMemoryRepository repository;
    private ExecutorService executor;
    private TransactionTemplate transactionTemplate;
    private MemoryService service;

    @BeforeEach
    void setUp() {
        memoryAgent = mock(MemoryAgent.class);
        repository = mock(UserMemoryRepository.class);
        executor = Executors.newSingleThreadExecutor();
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<?> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new MemoryService(memoryAgent, repository, executor, transactionTemplate);
    }

    @Test
    void generateMemoryAsync_submitsBothTasks() throws Exception {
        ReportResult report = new ReportResult("overall", "topic data", "error summary", 7, "takeaway");
        when(memoryAgent.mergeTopic(anyString(), anyString())).thenReturn("topic merged");
        when(memoryAgent.mergeProfile(anyString(), anyString())).thenReturn("profile merged");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.TOPIC_SUMMARY), any()))
                .thenReturn(Optional.of(new UserMemory("u1", MemoryType.TOPIC_SUMMARY, "old-topic", 1)));
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.LEARNING_PROFILE), isNull()))
                .thenReturn(Optional.of(new UserMemory("u1", MemoryType.LEARNING_PROFILE, "old-profile", 1)));

        service.generateMemoryAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);

        Thread.sleep(200);

        verify(memoryAgent, times(1)).mergeTopic(eq("old-topic"), eq("topic data"));
        verify(memoryAgent, times(1)).mergeProfile(eq("old-profile"), eq("error summary"));
        verify(repository, times(2)).save(any(UserMemory.class));
    }

    @Test
    void generateMemoryAsync_createsFirstVersionWhenNoPreviousMemory() throws Exception {
        ReportResult report = new ReportResult("new summary", "new topics", "new errors", 7, "takeaway");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(anyString(), any(MemoryType.class), any()))
                .thenReturn(Optional.empty());
        when(memoryAgent.mergeTopic(anyString(), anyString())).thenReturn("topic v1");
        when(memoryAgent.mergeProfile(anyString(), anyString())).thenReturn("profile v1");

        service.generateMemoryAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);
        Thread.sleep(200);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(repository, times(2)).save(captor.capture());

        UserMemory topic = captor.getAllValues().stream()
                .filter(m -> m.getType() == MemoryType.TOPIC_SUMMARY).findFirst().orElseThrow();
        assertThat(topic.getVersion()).isEqualTo(1);
        assertThat(topic.getContent()).isEqualTo("topic v1");

        UserMemory profile = captor.getAllValues().stream()
                .filter(m -> m.getType() == MemoryType.LEARNING_PROFILE).findFirst().orElseThrow();
        assertThat(profile.getVersion()).isEqualTo(1);
    }

    @Test
    void generateMemoryAsync_handlesMergeFailureGracefully() throws Exception {
        ReportResult report = new ReportResult("overall", "topics", "errors", 7, "takeaway");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(anyString(), any(MemoryType.class), any()))
                .thenReturn(Optional.empty());
        when(memoryAgent.mergeTopic(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));
        when(memoryAgent.mergeProfile(anyString(), anyString())).thenReturn("profile ok");

        service.generateMemoryAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);
        Thread.sleep(200);

        verify(memoryAgent, times(1)).mergeTopic(anyString(), anyString());
        verify(memoryAgent, times(1)).mergeProfile(anyString(), anyString());
        verify(repository, times(1)).save(any(UserMemory.class));
    }

    @Test
    void loadLatestContent_returnsContentWhenFound() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.TOPIC_SUMMARY, null))
                .thenReturn(Optional.of(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "my content", 3)));

        String result = service.loadLatestContent("user-1", MemoryType.TOPIC_SUMMARY, null);

        assertThat(result).isEqualTo("my content");
    }

    @Test
    void loadLatestContent_returnsEmptyWhenNoMemory() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.TOPIC_SUMMARY, null))
                .thenReturn(Optional.empty());

        String result = service.loadLatestContent("user-1", MemoryType.TOPIC_SUMMARY, null);

        assertThat(result).isEqualTo("");
    }

    @Test
    void loadLatestContent_returnsEmptyForStringTypeOverload() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.TOPIC_SUMMARY, null))
                .thenReturn(Optional.of(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "content", 1)));

        String result = service.loadLatestContent("user-1", "TOPIC_SUMMARY", null);

        assertThat(result).isEqualTo("content");
    }

    @Test
    void generateMemoryAsync_savesTopicMemoryWithMode() throws Exception {
        ReportResult report = new ReportResult("overall", "topic data", "errors", 7, "takeaway");
        when(memoryAgent.mergeTopic(anyString(), anyString())).thenReturn("topic merged");
        when(memoryAgent.mergeProfile(anyString(), anyString())).thenReturn("profile merged");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.TOPIC_SUMMARY), eq(AgentMode.DAILY_TALK)))
                .thenReturn(Optional.empty());
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.LEARNING_PROFILE), isNull()))
                .thenReturn(Optional.empty());

        service.generateMemoryAsync("user-1", report, AgentMode.DAILY_TALK);
        Thread.sleep(200);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(repository, times(2)).save(captor.capture());

        UserMemory topic = captor.getAllValues().stream()
                .filter(m -> m.getType() == MemoryType.TOPIC_SUMMARY).findFirst().orElseThrow();
        assertThat(topic.getMode()).isEqualTo(AgentMode.DAILY_TALK);
        assertThat(topic.getContent()).isEqualTo("topic merged");

        UserMemory profile = captor.getAllValues().stream()
                .filter(m -> m.getType() == MemoryType.LEARNING_PROFILE).findFirst().orElseThrow();
        assertThat(profile.getMode()).isNull();
    }

    @Test
    void loadLatestContent_queriesByMode() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.TOPIC_SUMMARY, AgentMode.DAILY_TALK))
                .thenReturn(Optional.of(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "daily topics", 3, AgentMode.DAILY_TALK)));

        String result = service.loadLatestContent("user-1", MemoryType.TOPIC_SUMMARY, AgentMode.DAILY_TALK);

        assertThat(result).isEqualTo("daily topics");
        verify(repository).findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.TOPIC_SUMMARY, AgentMode.DAILY_TALK);
    }

    @Test
    void loadLatestContent_queriesNullModeForLearningProfile() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.LEARNING_PROFILE, null))
                .thenReturn(Optional.of(new UserMemory("user-1", MemoryType.LEARNING_PROFILE, "profile data", 2)));

        String result = service.loadLatestContent("user-1", MemoryType.LEARNING_PROFILE, null);

        assertThat(result).isEqualTo("profile data");
        verify(repository).findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", MemoryType.LEARNING_PROFILE, null);
    }

    @Test
    void generateMemoryAsync_passesSessionIdToSavedRecords() throws Exception {
        ReportResult report = new ReportResult("overall", "topic data", "errors", 7, "takeaway");
        when(memoryAgent.mergeTopic(anyString(), anyString())).thenReturn("topic merged");
        when(memoryAgent.mergeProfile(anyString(), anyString())).thenReturn("profile merged");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.TOPIC_SUMMARY), any()))
                .thenReturn(Optional.empty());
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(MemoryType.LEARNING_PROFILE), isNull()))
                .thenReturn(Optional.empty());

        service.generateMemoryAsync("user-1", report, AgentMode.WORKPLACE_STANDUP, "session-abc-123");
        Thread.sleep(200);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(repository, times(2)).save(captor.capture());

        captor.getAllValues().forEach(memory ->
                assertThat(memory.getSessionId()).isEqualTo("session-abc-123"));
    }
}
