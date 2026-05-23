package com.hugosol.webagent.e2e;

import com.hugosol.webagent.e2e.helper.E2ETestBase;
import com.hugosol.webagent.e2e.helper.WireMockStubs;
import com.hugosol.webagent.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyTalkIT extends E2ETestBase {

    @Test
    void fullDailyTalkSessionWithTeachingStyle() {
        WireMockStubs.registerDailyTalkStubs(wireMockServer);

        startSession("DAILY_TALK");

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage after session start");

        sendMessage("I go to a party last weekend. I very happy.");
        waitForAgentResponse();
        takeScreenshot("daily-round1");

        sendMessage("We eat at a Sichuan restaurant. The food is very delicious.");
        waitForAgentResponse();
        takeScreenshot("daily-round2");

        sendMessage("Next Friday I will meet my friends. We plan to go somewhere fun.");
        waitForAgentResponse();
        takeScreenshot("daily-round3");

        assertEquals(3, countUserMessages(), "should have 3 user messages");
        assertTrue(countAgentMessages() >= 3, "should have at least 3 agent messages from streaming");
        assertTrue(countCorrectionBubbles() >= 2, "should have at least 2 correction summary bubbles");
        assertTrue(hasCorrectionBubbleWith("I go"), "should contain round 1 correction original text");

        assertEquals(4, countCorrectionSidebarItems(), "sidebar should have 4 correction items (2+1+1)");

        endSession();
        takeScreenshot("daily-report");

        assertTrue(isReportModalVisible(), "report modal should be visible");
        String reportText = getReportModalText();
        assertTrue(reportText.contains("Topic Summary"), "report should show topic summary section");

        verifyH2Data(sid);
    }

    private void verifyH2Data(String sid) {
        Session session = sessionRepository.findById(sid).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session.getStatus(), "session should be completed");
        assertEquals(AgentMode.DAILY_TALK, session.getMode(), "session mode should be DAILY_TALK");
        assertNotNull(session.getEndTime(), "session should have endTime");

        List<Message> messages = messageRepository.findBySessionIdOrderByCreateTimeAsc(sid);
        assertEquals(6, messages.size(), "should have 6 messages (3 USER + 3 AGENT)");
        assertEquals(MessageRole.USER, messages.get(0).getRole());
        assertEquals(MessageRole.AGENT, messages.get(1).getRole());

        List<ErrorRecord> errors = errorRecordRepository.findBySessionId(sid);
        assertTrue(errors.size() >= 2, "should have at least 2 error records");

        SessionReport report = sessionReportRepository.findBySessionId(sid).orElseThrow();
        assertNotNull(report.getSummary(), "report summary should not be null");

        var topicMemory = userMemoryRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.TOPIC_SUMMARY, AgentMode.DAILY_TALK);
        assertTrue(topicMemory.isPresent(), "TOPIC_SUMMARY should exist with mode=DAILY_TALK");
        assertEquals(AgentMode.DAILY_TALK, topicMemory.get().getMode());

        var learningProfile = userMemoryRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.LEARNING_PROFILE, null);
        assertTrue(learningProfile.isPresent(), "LEARNING_PROFILE should exist");
        assertNull(learningProfile.get().getMode(), "LEARNING_PROFILE mode should be null (cross-mode shared)");
    }
}
