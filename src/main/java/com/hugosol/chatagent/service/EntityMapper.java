package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.ReportAgent.ReportResult;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.ErrorRecord;
import com.hugosol.chatagent.model.Message;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.model.SessionReport;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class EntityMapper {

    List<Message> buildMessages(String sessionId, List<MessageData> data) {
        List<Message> messages = new ArrayList<>();
        for (MessageData md : data) {
            messages.add(new Message(sessionId, md.getRole(), md.getContent(),
                    md.getMessageId(), md.getTokenCount()));
        }
        return messages;
    }

    List<ErrorRecord> buildErrorRecords(String sessionId,
                                         List<CorrectionData> corrections,
                                         List<Message> savedMessages) {
        List<ErrorRecord> records = new ArrayList<>();
        for (CorrectionData cd : corrections) {
            for (Message msg : savedMessages) {
                if (msg.getMessageId() != null
                        && msg.getMessageId() == cd.getMessageId()
                        && msg.getRole() == MessageRole.USER) {
                    records.add(new ErrorRecord(
                            sessionId, msg.getId(), cd.getType(),
                            cd.getOriginal(), cd.getCorrected(), cd.getExplanation()));
                    break;
                }
            }
        }
        return records;
    }

    SessionReport buildReport(String sessionId, ReportResult report) {
        SessionReport sr = new SessionReport(sessionId);
        sr.setSummary(report.overallAssessment());
        sr.setErrorSummary(report.errorSummary());
        sr.setFluencyScore(report.fluencyScore());
        sr.setKeyTakeaway(report.keyTakeaway());
        return sr;
    }
}
