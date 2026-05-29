package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.*;
import com.hugosol.webagent.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final EntityMapper mapper;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final SessionReportRepository sessionReportRepository;
    private final UserProgressRepository userProgressRepository;

    public SessionStore(EntityMapper mapper,
                          SessionRepository sessionRepository,
                          MessageRepository messageRepository,
                          ErrorRecordRepository errorRecordRepository,
                          SessionReportRepository sessionReportRepository,
                          UserProgressRepository userProgressRepository) {
        this.mapper = mapper;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.errorRecordRepository = errorRecordRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.userProgressRepository = userProgressRepository;
    }

    @Transactional
    public Session createSession(AgentMode mode, String userId) {
        Session session = new Session(mode);
        session.setUserId(userId);
        session = sessionRepository.save(session);
        log.info("SessionStore: created session {} for user {}", session.getId(), userId);
        return session;
    }

    @Transactional
    public SessionReport completeSession(String sessionId,
                                           List<MessageData> messages,
                                           List<CorrectionData> corrections,
                                           ReportResult report) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (report != null) {
            session.complete();
        } else {
            session.setStatus(SessionStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
        }
        sessionRepository.save(session);

        List<Message> savedMessages = mapper.buildMessages(sessionId, messages);
        messageRepository.saveAll(savedMessages);

        List<ErrorRecord> errorRecords = mapper.buildErrorRecords(sessionId, corrections, savedMessages);
        errorRecordRepository.saveAll(errorRecords);

        if (report != null) {
            SessionReport sessionReport = mapper.buildReport(sessionId, report);
            sessionReportRepository.save(sessionReport);
            updateUserProgress(session);
            log.info("SessionStore: completed session {}", sessionId);
            return sessionReport;
        } else {
            updateUserProgress(session);
            log.info("SessionStore: failed session {}", sessionId);
            return null;
        }
    }

    public List<Session> getHistory(String userId) {
        return sessionRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    private void updateUserProgress(Session session) {
        UserProgress progress = userProgressRepository.findByUserId(session.getUserId())
                .orElseGet(UserProgress::new);
        progress.setUserId(session.getUserId());

        progress.setTotalSessions(progress.getTotalSessions() + 1);

        long sessionMinutes = Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
        progress.setTotalMinutes(progress.getTotalMinutes() + sessionMinutes);

        userProgressRepository.save(progress);
    }
}
