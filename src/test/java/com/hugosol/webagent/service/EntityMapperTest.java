package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.ErrorRecord;
import com.hugosol.webagent.model.ErrorType;
import com.hugosol.webagent.model.Message;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.model.SessionReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMapperTest {

    private final EntityMapper mapper = new EntityMapper();

    @Test
    void buildMessagesEmptyList() {
        List<Message> result = mapper.buildMessages("s1", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void buildMessagesSingleEntry() {
        MessageData md = new MessageData(MessageRole.USER, "Hello", 1);
        List<Message> result = mapper.buildMessages("s1", List.of(md));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        assertThat(result.get(0).getSessionId()).isEqualTo("s1");
    }

    @Test
    void buildMessagesMultipleEntriesPropagatesSessionId() {
        List<MessageData> data = List.of(
                new MessageData(MessageRole.USER, "Hi", 1),
                new MessageData(MessageRole.AGENT, "Hello there", 1)
        );
        List<Message> result = mapper.buildMessages("session-abc", data);

        assertThat(result).hasSize(2);
        for (Message msg : result) {
            assertThat(msg.getSessionId()).isEqualTo("session-abc");
        }
    }

    @Test
    void buildMessagesMixedRoles() {
        List<MessageData> data = List.of(
                new MessageData(MessageRole.USER, "A", 1),
                new MessageData(MessageRole.AGENT, "B", 1),
                new MessageData(MessageRole.CORRECTION, "C", 1)
        );
        List<Message> result = mapper.buildMessages("s1", data);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(result.get(1).getRole()).isEqualTo(MessageRole.AGENT);
        assertThat(result.get(2).getRole()).isEqualTo(MessageRole.CORRECTION);
    }

    @Test
    void buildErrorRecordsEmptyCorrections() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "Hi", null, null)
        );
        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(), savedMessages);

        assertThat(result).isEmpty();
    }

    @Test
    void buildErrorRecordsSingleCorrectionMapsToUserMessage() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "he go", 1, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "he go", "he goes", "第三人称");
        cd.setMessageId(1);

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);

        assertThat(result).hasSize(1);
        ErrorRecord record = result.get(0);
        assertThat(record.getSessionId()).isEqualTo("s1");
        assertThat(record.getType()).isEqualTo(ErrorType.GRAMMAR);
        assertThat(record.getOriginalText()).isEqualTo("he go");
        assertThat(record.getCorrectedText()).isEqualTo("he goes");
        assertThat(record.getExplanation()).isEqualTo("第三人称");
        assertThat(record.getMessageDbId()).isEqualTo(savedMessages.get(0).getId());
    }

    @Test
    void buildErrorRecordsSkipsNonUserMessages() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.AGENT, "How are you?", null, null),
                new Message("s1", MessageRole.USER, "I fine", 1, null),
                new Message("s1", MessageRole.AGENT, "Great!", null, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "I fine", "I'm fine", "缺少be动词");
        cd.setMessageId(1); // messageId 1 → userMessages index 0

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageDbId()).isEqualTo(savedMessages.get(1).getId());
    }

    @Test
    void buildErrorRecordsMessageIdOutOfRangeSkipped() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "First", null, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "x", "y", "z");
        cd.setMessageId(5); // no 5th user message

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);
        assertThat(result).isEmpty();
    }

    @Test
    void buildErrorRecordsMessageIdZeroSkipped() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "First", null, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "x", "y", "z");
        cd.setMessageId(0); // 0-indexed → idx = -1 → out of range

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);
        assertThat(result).isEmpty();
    }

    @Test
    void buildErrorRecordsNoUserMessagesAllSkipped() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.AGENT, "Hi", null, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "x", "y", "z");
        cd.setMessageId(1);

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);
        assertThat(result).isEmpty();
    }

    @Test
    void buildErrorRecordsMultipleCorrectionsToOneMessage() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "I go to park", 1, null)
        );

        CorrectionData cd1 = new CorrectionData(ErrorType.GRAMMAR, "go", "went", "时态");
        cd1.setMessageId(1);
        CorrectionData cd2 = new CorrectionData(ErrorType.GRAMMAR, "to park", "to the park", "冠词");
        cd2.setMessageId(1);

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd1, cd2), savedMessages);

        assertThat(result).hasSize(2);
        String msgId = savedMessages.get(0).getId();
        assertThat(result.get(0).getMessageDbId()).isEqualTo(msgId);
        assertThat(result.get(1).getMessageDbId()).isEqualTo(msgId);
    }

    @Test
    void buildErrorRecordsMatchesByMessageIdDirectly() {
        List<Message> savedMessages = List.of(
                new Message("s1", MessageRole.USER, "First", 3, null),
                new Message("s1", MessageRole.AGENT, "Reply", 3, null),
                new Message("s1", MessageRole.USER, "Second", 1, null),
                new Message("s1", MessageRole.AGENT, "Reply", 1, null)
        );

        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "x", "y", "z");
        cd.setMessageId(3);

        List<ErrorRecord> result = mapper.buildErrorRecords("s1", List.of(cd), savedMessages);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageDbId()).isEqualTo(savedMessages.get(0).getId());
    }

    @Test
    void buildReportPopulatesAllFields() {
        ReportResult report = new ReportResult(
                "Overall assessment text",
                "Grammar: 3, Word Choice: 2",
                7,
                "Practice articles"
        );

        SessionReport sr = mapper.buildReport("s1", report);

        assertThat(sr.getSessionId()).isEqualTo("s1");
        assertThat(sr.getSummary()).isEqualTo("Overall assessment text");
        assertThat(sr.getErrorSummary()).isEqualTo("Grammar: 3, Word Choice: 2");
        assertThat(sr.getFluencyScore()).isEqualTo(7);
        assertThat(sr.getKeyTakeaway()).isEqualTo("Practice articles");
    }

    @Test
    void buildMessagesPreservesMessageId() {
        MessageData md = new MessageData(MessageRole.USER, "Hello", 42);
        List<Message> result = mapper.buildMessages("s1", List.of(md));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageId()).isEqualTo(42);
    }

    @Test
    void buildMessagesPreservesTokenCountWhenSet() {
        MessageData md = new MessageData(MessageRole.AGENT, "Hello", 1);
        md.setTokenCount(520);
        List<Message> result = mapper.buildMessages("s1", List.of(md));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTokenCount()).isEqualTo(520);
    }

    @Test
    void buildMessagesTokenCountNullWhenNotSet() {
        MessageData md = new MessageData(MessageRole.USER, "Hello", 1);
        List<Message> result = mapper.buildMessages("s1", List.of(md));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTokenCount()).isNull();
    }

    @Test
    void buildReportHandlesEmptyValues() {
        ReportResult report = new ReportResult("", "", 0, "");

        SessionReport sr = mapper.buildReport("s1", report);

        assertThat(sr.getSummary()).isEmpty();
        assertThat(sr.getFluencyScore()).isZero();
        assertThat(sr.getKeyTakeaway()).isEmpty();
    }
}
