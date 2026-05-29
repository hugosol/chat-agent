package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCompleteTest {

    @Mock
    private SessionDbStore sessionStore;

    @Mock
    private ReportAgent reportAgent;

    @Mock
    private LearningProfileService learningProfileService;

    @Mock
    private MemoryCueService memoryCueService;

    private SessionComplete sessionComplete;

    @BeforeEach
    void setUp() {
        sessionComplete = new SessionComplete(sessionStore, reportAgent, learningProfileService, memoryCueService);
    }

    @Test
    void complete_AllSuccess_ReturnsValidReport() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "Hello", 1));
        List<CorrectionData> corrections = List.of();
        ReportResult expectedReport = new ReportResult("Great job", "none", 7, "keep practicing");
        when(reportAgent.generate(any(), any(), any())).thenReturn(expectedReport);

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.WORKPLACE_STANDUP);

        assertThat(result).isEqualTo(expectedReport);
        verify(sessionStore).completeSession(eq("s1"), eq(messages), eq(corrections), eq(expectedReport));
        verify(learningProfileService).generateLearningProfileAsync("user1", expectedReport, AgentMode.WORKPLACE_STANDUP, "s1");
        verify(memoryCueService).generateCuesAsync("s1", "user1", AgentMode.WORKPLACE_STANDUP, messages);
    }

    @Test
    void complete_ReportFails_ReturnsFallback_StillPersistsAndFiresMemory() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "Hi", 1));
        List<CorrectionData> corrections = List.of();
        when(reportAgent.generate(any(), any(), any())).thenThrow(new RuntimeException("LLM unavailable"));

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.WORKPLACE_STANDUP);

        assertThat(result.fluencyScore()).isEqualTo(-1);
        assertThat(result.overallAssessment()).contains("failed");
        verify(sessionStore).completeSession(eq("s1"), eq(messages), eq(corrections), isNull());
        verify(learningProfileService).generateLearningProfileAsync(eq("user1"), any(), eq(AgentMode.WORKPLACE_STANDUP), eq("s1"));
        verify(memoryCueService).generateCuesAsync("s1", "user1", AgentMode.WORKPLACE_STANDUP, messages);
    }

    @Test
    void complete_PersistFails_ReturnsReport_StillFiresMemory() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "Hey", 1));
        List<CorrectionData> corrections = List.of();
        ReportResult expectedReport = new ReportResult("Good", "minor", 6, "tip");
        when(reportAgent.generate(any(), any(), any())).thenReturn(expectedReport);
        when(sessionStore.completeSession(any(), any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.WORKPLACE_STANDUP);

        assertThat(result).isEqualTo(expectedReport);
        verify(learningProfileService).generateLearningProfileAsync("user1", expectedReport, AgentMode.WORKPLACE_STANDUP, "s1");
        verify(memoryCueService).generateCuesAsync("s1", "user1", AgentMode.WORKPLACE_STANDUP, messages);
    }

    @Test
    void complete_DoubleFailure_ReturnsFallback_StillFiresMemory() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "Hi", 1));
        List<CorrectionData> corrections = List.of();
        when(reportAgent.generate(any(), any(), any())).thenThrow(new RuntimeException("LLM down"));
        when(sessionStore.completeSession(any(), any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.WORKPLACE_STANDUP);

        assertThat(result.fluencyScore()).isEqualTo(-1);
        verify(learningProfileService).generateLearningProfileAsync(eq("user1"), any(), eq(AgentMode.WORKPLACE_STANDUP), eq("s1"));
        verify(memoryCueService).generateCuesAsync("s1", "user1", AgentMode.WORKPLACE_STANDUP, messages);
    }

    @Test
    void complete_NullUserId_SkipsMemory() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "Hello", 1));
        List<CorrectionData> corrections = List.of();
        ReportResult expectedReport = new ReportResult("OK", "none", 5, "tip");
        when(reportAgent.generate(any(), any(), any())).thenReturn(expectedReport);

        ReportResult result = sessionComplete.complete("s1", messages, corrections, null, AgentMode.WORKPLACE_STANDUP);

        assertThat(result).isEqualTo(expectedReport);
        verify(sessionStore).completeSession(eq("s1"), eq(messages), eq(corrections), eq(expectedReport));
        verify(learningProfileService, never()).generateLearningProfileAsync(any(), any(), any(), any());
        verify(memoryCueService, never()).generateCuesAsync(any(), any(), any(), any());
    }
}
