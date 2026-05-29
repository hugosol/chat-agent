package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.*;
import com.hugosol.webagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionDbStoreTest {

    @Mock
    private EntityMapper mapper;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ErrorRecordRepository errorRecordRepository;

    @Mock
    private SessionReportRepository sessionReportRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    private SessionDbStore store;

    @BeforeEach
    void setUp() {
        store = new SessionDbStore(mapper, sessionRepository, messageRepository,
                errorRecordRepository, sessionReportRepository, userProgressRepository);
    }

    @Test
    void createSessionReturnsSavedSession() {
        Session saved = new Session(AgentMode.WORKPLACE_STANDUP);
        when(sessionRepository.save(any(Session.class))).thenReturn(saved);

        Session result = store.createSession(AgentMode.WORKPLACE_STANDUP, "user1");

        assertThat(result).isSameAs(saved);
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void completeSessionSavesAllEntitiesInSequence() {
        Session session = new Session(AgentMode.WORKPLACE_STANDUP);
        session.setUserId("user1");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        List<Message> savedMessages = List.of(new Message("s1", MessageRole.USER, "Hi", null, null));
        when(mapper.buildMessages(any(), anyList())).thenReturn(savedMessages);
        when(messageRepository.saveAll(savedMessages)).thenReturn(savedMessages);

        List<ErrorRecord> errorRecords = List.of();
        when(mapper.buildErrorRecords(any(), anyList(), anyList())).thenReturn(errorRecords);
        when(errorRecordRepository.saveAll(errorRecords)).thenReturn(errorRecords);

        SessionReport report = new SessionReport("s1");
        when(mapper.buildReport(any(), any(ReportResult.class))).thenReturn(report);
        when(sessionReportRepository.save(report)).thenReturn(report);

        when(userProgressRepository.findByUserId("user1")).thenReturn(Optional.empty());

        SessionReport result = store.completeSession("s1",
                List.of(new MessageData(MessageRole.USER, "Hi", 1)),
                List.of(),
                new ReportResult("ok", "", 5, ""));

        assertThat(result).isSameAs(report);
        verify(sessionRepository).save(session);
        verify(messageRepository).saveAll(savedMessages);
        verify(errorRecordRepository).saveAll(errorRecords);
        verify(sessionReportRepository).save(report);
    }

    @Test
    void completeSessionThrowsWhenSessionNotFound() {
        when(sessionRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.completeSession("unknown",
                List.of(), List.of(),
                new ReportResult("", "", 0, "")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void completeSessionUpdatesUserProgress() {
        Session session = new Session(AgentMode.WORKPLACE_STANDUP);
        session.setUserId("user1");
        session.complete();
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);
        when(mapper.buildMessages(any(), anyList())).thenReturn(List.of());
        when(mapper.buildErrorRecords(any(), anyList(), anyList())).thenReturn(List.of());
        SessionReport sr = new SessionReport("s1");
        when(mapper.buildReport(any(), any())).thenReturn(sr);
        when(sessionReportRepository.save(sr)).thenReturn(sr);

        UserProgress existing = new UserProgress();
        when(userProgressRepository.findByUserId("user1")).thenReturn(Optional.of(existing));
        when(userProgressRepository.save(any(UserProgress.class))).thenReturn(existing);

        store.completeSession("s1", List.of(), List.of(),
                new ReportResult("", "", 0, ""));

        verify(userProgressRepository).save(any(UserProgress.class));
    }

    @Test
    void getHistoryReturnsRepositoryResults() {
        List<Session> sessions = List.of(
                new Session(AgentMode.WORKPLACE_STANDUP)
        );
        when(sessionRepository.findByUserIdOrderByStartTimeDesc("user1")).thenReturn(sessions);

        List<Session> result = store.getHistory("user1");

        assertThat(result).isSameAs(sessions);
    }

    @Test
    void getHistoryReturnsEmptyList() {
        when(sessionRepository.findByUserIdOrderByStartTimeDesc("user1")).thenReturn(List.of());

        List<Session> result = store.getHistory("user1");

        assertThat(result).isEmpty();
    }

    @Test
    void completeSession_NullReport_MarksSessionFailed() {
        Session session = new Session(AgentMode.WORKPLACE_STANDUP);
        session.setUserId("user1");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);
        when(mapper.buildMessages(any(), anyList())).thenReturn(List.of());
        when(mapper.buildErrorRecords(any(), anyList(), anyList())).thenReturn(List.of());
        when(userProgressRepository.findByUserId("user1")).thenReturn(Optional.empty());

        store.completeSession("s1",
                List.of(new MessageData(MessageRole.USER, "Hi", 1)),
                List.of(),
                null);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(session.getEndTime()).isNotNull();
        verify(sessionReportRepository, never()).save(any());
        verify(messageRepository).saveAll(any());
        verify(errorRecordRepository).saveAll(any());
    }
}
