package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
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
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final SessionReportRepository sessionReportRepository;
    private final UserProgressRepository userProgressRepository;

    public SessionService(SessionRepository sessionRepository,
                          MessageRepository messageRepository,
                          ErrorRecordRepository errorRecordRepository,
                          SessionReportRepository sessionReportRepository,
                          UserProgressRepository userProgressRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.errorRecordRepository = errorRecordRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.userProgressRepository = userProgressRepository;
    }

    @Transactional
    public Session createSession(ScenarioType scenario, String persona) {
        String threadId = java.util.UUID.randomUUID().toString();
        Session session = new Session(scenario, persona, threadId);
        session = sessionRepository.save(session);
        log.info("SessionService: created session {} with scenario={}", session.getId(), scenario);
        return session;
    }

    @Transactional
    public SessionReport completeSession(String sessionId,
                                          List<MessageData> messages,
                                          List<CorrectionData> corrections,
                                          ReportResult report) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        session.complete();
        sessionRepository.save(session);

        for (MessageData md : messages) {
            MessageRole role = switch (md.getRole()) {
                case "USER" -> MessageRole.USER;
                case "AGENT" -> MessageRole.AGENT;
                case "CORRECTION" -> MessageRole.CORRECTION;
                default -> MessageRole.AGENT;
            };
            Message msg = new Message(sessionId, role, md.getContent());
            messageRepository.save(msg);

            if (role == MessageRole.USER) {
                for (CorrectionData cd : corrections) {
                    ErrorType type = parseErrorType(cd.getType());
                    ErrorRecord er = new ErrorRecord(
                            sessionId, msg.getId(), type,
                            cd.getOriginal(), cd.getCorrected(), cd.getExplanation());
                    errorRecordRepository.save(er);
                }
            }
        }

        SessionReport sessionReport = new SessionReport(sessionId);
        sessionReport.setSummary(report.overallAssessment());
        sessionReport.setErrorSummary(report.errorSummary());
        sessionReport.setVocabularySuggestions(report.vocabularySuggestions());
        sessionReport.setFluencyScore(report.fluencyScore());
        sessionReport.setKeyTakeaway(report.keyTakeaway());
        sessionReport = sessionReportRepository.save(sessionReport);

        updateUserProgress(session);

        log.info("SessionService: completed session {}", sessionId);
        return sessionReport;
    }

    public List<Session> getHistory() {
        return sessionRepository.findAllByOrderByStartTimeDesc();
    }

    private ErrorType parseErrorType(String type) {
        try {
            return ErrorType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ErrorType.GRAMMAR;
        }
    }

    private void updateUserProgress(Session session) {
        List<UserProgress> progressList = userProgressRepository.findAll();
        UserProgress progress;
        if (progressList.isEmpty()) {
            progress = new UserProgress();
        } else {
            progress = progressList.get(0);
        }

        progress.setTotalSessions(progress.getTotalSessions() + 1);

        long sessionMinutes = Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
        progress.setTotalMinutes(progress.getTotalMinutes() + sessionMinutes);
        progress.setUpdatedAt(LocalDateTime.now());

        userProgressRepository.save(progress);
    }
}
