package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.MemoryCueAgent;
import com.hugosol.chatagent.agent.ReportAgent;
import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
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
    private AssertionService assertionService;

    @Mock
    private MemoryCueAgent memoryCueAgent;

    @Mock
    private MemoryCueService memoryCueService;

    private SessionComplete sessionComplete;

    @BeforeEach
    void setUp() {
        sessionComplete = new SessionComplete(sessionStore, reportAgent, learningProfileService,
                assertionService, memoryCueAgent, memoryCueService);
        lenient().when(memoryCueAgent.detectSwitches(any(), any(), any()))
                .thenReturn(Collections.emptyList());
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
        verify(assertionService).generateAssertionsAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
        verify(memoryCueService).generateCuesAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
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
        verify(assertionService).generateAssertionsAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
        verify(memoryCueService).generateCuesAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
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
        verify(assertionService).generateAssertionsAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
        verify(memoryCueService).generateCuesAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
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
        verify(assertionService).generateAssertionsAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
        verify(memoryCueService).generateCuesAsync(eq("s1"), eq("user1"), eq(AgentMode.WORKPLACE_STANDUP),
                argThat(segs -> segs.size() == 1 && segs.get(0).equals(messages)));
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
        verify(assertionService, never()).generateAssertionsAsync(any(), any(), any(), any());
        verify(memoryCueService, never()).generateCuesAsync(any(), any(), any(), any());
        verify(memoryCueAgent, never()).detectSwitches(any(), any(), any());
    }

    @Test
    void japaneseModeSkipsLearningProfileAndMemoryCues() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "こんにちは", 1));
        List<CorrectionData> corrections = List.of();
        ReportResult expectedReport = new ReportResult("良好", "none", 6, "敬語を練習");
        when(reportAgent.generate(any(), any(), any())).thenReturn(expectedReport);

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.JAPANESE_BUSINESS);

        assertThat(result).isEqualTo(expectedReport);
        verify(sessionStore).completeSession(eq("s1"), eq(messages), eq(corrections), eq(expectedReport));
        verify(learningProfileService, never()).generateLearningProfileAsync(any(), any(), any(), any());
        verify(assertionService, never()).generateAssertionsAsync(any(), any(), any(), any());
        verify(memoryCueService, never()).generateCuesAsync(any(), any(), any(), any());
        verify(memoryCueAgent, never()).detectSwitches(any(), any(), any());
    }

    @Test
    void japaneseModeStillGeneratesReportAndPersists() {
        List<MessageData> messages = List.of(new MessageData(MessageRole.USER, "おはよう", 1));
        List<CorrectionData> corrections = List.of();
        ReportResult expectedReport = new ReportResult("良い", "none", 7, "続けて");
        when(reportAgent.generate(any(), any(), any())).thenReturn(expectedReport);

        ReportResult result = sessionComplete.complete("s1", messages, corrections, "user1", AgentMode.JAPANESE_BUSINESS);

        assertThat(result).isEqualTo(expectedReport);
        verify(sessionStore).completeSession(eq("s1"), eq(messages), eq(corrections), eq(expectedReport));
        verify(reportAgent).generate(eq(messages), eq(corrections), any());
        verify(learningProfileService, never()).generateLearningProfileAsync(any(), any(), any(), any());
        verify(assertionService, never()).generateAssertionsAsync(any(), any(), any(), any());
        verify(memoryCueService, never()).generateCuesAsync(any(), any(), any(), any());
        verify(memoryCueAgent, never()).detectSwitches(any(), any(), any());
    }
}
