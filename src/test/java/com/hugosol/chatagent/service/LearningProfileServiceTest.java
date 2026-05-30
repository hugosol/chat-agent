package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.LearningAgent;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.LearningType;
import com.hugosol.chatagent.model.UserLearningProfile;
import com.hugosol.chatagent.repository.UserLearningProfileRepository;
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

class LearningProfileServiceTest {

    private LearningAgent learningAgent;
    private UserLearningProfileRepository repository;
    private ExecutorService executor;
    private TransactionTemplate transactionTemplate;
    private LearningProfileService service;

    @BeforeEach
    void setUp() {
        learningAgent = mock(LearningAgent.class);
        repository = mock(UserLearningProfileRepository.class);
        executor = Executors.newSingleThreadExecutor();
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<?> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new LearningProfileService(learningAgent, repository, executor, transactionTemplate);
    }

    @Test
    void generateLearningProfileAsync_mergesLearningProfile() throws Exception {
        ReportResult report = new ReportResult("overall", "error summary", 7, "takeaway");
        when(learningAgent.mergeProfile(anyString(), anyString(), any())).thenReturn("profile merged");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(LearningType.LEARNING_PROFILE), isNull()))
                .thenReturn(Optional.of(new UserLearningProfile("u1", LearningType.LEARNING_PROFILE, "old-profile", 1)));

        service.generateLearningProfileAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);

        Thread.sleep(200);

        verify(learningAgent, times(1)).mergeProfile(eq("old-profile"), eq("error summary"), any());
        verify(repository, times(1)).save(any(UserLearningProfile.class));

        ArgumentCaptor<UserLearningProfile> captor = ArgumentCaptor.forClass(UserLearningProfile.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LearningType.LEARNING_PROFILE);
        assertThat(captor.getValue().getContent()).isEqualTo("profile merged");
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void generateLearningProfileAsync_createsFirstVersionWhenNoPreviousMemory() throws Exception {
        ReportResult report = new ReportResult("new summary", "new errors", 7, "takeaway");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(anyString(), any(LearningType.class), any()))
                .thenReturn(Optional.empty());
        when(learningAgent.mergeProfile(anyString(), anyString(), any())).thenReturn("profile v1");

        service.generateLearningProfileAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);
        Thread.sleep(200);

        ArgumentCaptor<UserLearningProfile> captor = ArgumentCaptor.forClass(UserLearningProfile.class);
        verify(repository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo(LearningType.LEARNING_PROFILE);
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getContent()).isEqualTo("profile v1");
    }

    @Test
    void generateLearningProfileAsync_handlesProfileMergeFailureGracefully() throws Exception {
        ReportResult report = new ReportResult("overall", "errors", 7, "takeaway");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(anyString(), any(LearningType.class), any()))
                .thenReturn(Optional.empty());
        when(learningAgent.mergeProfile(anyString(), anyString(), any())).thenThrow(new RuntimeException("LLM error"));

        service.generateLearningProfileAsync("user-1", report, AgentMode.WORKPLACE_STANDUP);
        Thread.sleep(200);

        verify(learningAgent, times(1)).mergeProfile(anyString(), anyString(), any());
        verify(repository, never()).save(any(UserLearningProfile.class));
    }

    @Test
    void loadLatestContent_returnsContentWhenFound() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", LearningType.LEARNING_PROFILE, null))
                .thenReturn(Optional.of(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "my content", 3)));

        String result = service.loadLatestContent("user-1", LearningType.LEARNING_PROFILE, null);

        assertThat(result).isEqualTo("my content");
    }

    @Test
    void loadLatestContent_returnsEmptyWhenNoMemory() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", LearningType.LEARNING_PROFILE, null))
                .thenReturn(Optional.empty());

        String result = service.loadLatestContent("user-1", LearningType.LEARNING_PROFILE, null);

        assertThat(result).isEqualTo("");
    }

    @Test
    void loadLatestContent_returnsEmptyForStringTypeOverload() {
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc("user-1", LearningType.LEARNING_PROFILE, null))
                .thenReturn(Optional.of(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "content", 1)));

        String result = service.loadLatestContent("user-1", "LEARNING_PROFILE", null);

        assertThat(result).isEqualTo("content");
    }

    @Test
    void generateLearningProfileAsync_passesSessionIdToSavedRecords() throws Exception {
        ReportResult report = new ReportResult("overall", "errors", 7, "takeaway");
        when(learningAgent.mergeProfile(anyString(), anyString(), any())).thenReturn("profile merged");
        when(repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(eq("user-1"), eq(LearningType.LEARNING_PROFILE), isNull()))
                .thenReturn(Optional.empty());

        service.generateLearningProfileAsync("user-1", report, AgentMode.WORKPLACE_STANDUP, "session-abc-123");
        Thread.sleep(200);

        ArgumentCaptor<UserLearningProfile> captor = ArgumentCaptor.forClass(UserLearningProfile.class);
        verify(repository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getSessionId()).isEqualTo("session-abc-123");
    }
}
