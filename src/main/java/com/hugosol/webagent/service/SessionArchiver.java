package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import com.hugosol.webagent.model.ErrorRecord;
import com.hugosol.webagent.model.Message;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.model.SessionReport;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class SessionArchiver {

    List<Message> buildMessages(String sessionId, List<MessageData> data) {
        List<Message> messages = new ArrayList<>();
        for (MessageData md : data) {
            messages.add(new Message(sessionId, md.getRole(), md.getContent()));
        }
        return messages;
    }

    List<ErrorRecord> buildErrorRecords(String sessionId,
                                         List<CorrectionData> corrections,
                                         List<Message> savedMessages) {
        List<Message> userMessages = new ArrayList<>();
        for (Message msg : savedMessages) {
            if (msg.getRole() == MessageRole.USER) {
                userMessages.add(msg);
            }
        }

        List<ErrorRecord> records = new ArrayList<>();
        for (CorrectionData cd : corrections) {
            int idx = cd.getMessageId() - 1;
            if (idx >= 0 && idx < userMessages.size()) {
                String targetMessageDbId = userMessages.get(idx).getId();
                records.add(new ErrorRecord(
                        sessionId, targetMessageDbId, cd.getType(),
                        cd.getOriginal(), cd.getCorrected(), cd.getExplanation()));
            }
        }
        return records;
    }

    SessionReport buildReport(String sessionId, ReportResult report) {
        SessionReport sr = new SessionReport(sessionId);
        sr.setSummary(report.overallAssessment());
        sr.setErrorSummary(report.errorSummary());
        sr.setVocabularySuggestions(report.vocabularySuggestions());
        sr.setFluencyScore(report.fluencyScore());
        sr.setKeyTakeaway(report.keyTakeaway());
        return sr;
    }
}
