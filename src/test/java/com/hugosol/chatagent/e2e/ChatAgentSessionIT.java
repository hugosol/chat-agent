package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAgentSessionIT extends E2ETestBase {

    @Test
    void fullSessionWithMultiTurnAndSidebar() {
        startSession(AgentMode.WORKPLACE_STANDUP.name());

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage after session start");

        sendMessage("Yesterday I go to office and meet my colleague. He say we need finish the project this week.");
        waitForAgentResponse();
        takeScreenshot("round1");

        sendMessage("I think is very hard. We don't have enough people.");
        waitForAgentResponse();
        takeScreenshot("round2");

        sendMessage("Ok I will try my best. Yesterday I already finished most part.");
        waitForAgentResponse();
        takeScreenshot("round3");

        assertEquals(3, countUserMessages(), "should have 3 user messages");
        assertTrue(countAgentMessages() >= 3, "should have at least 3 agent messages from streaming");
        assertEquals(3, countCorrectionBubbles(), "should have 3 correction summary bubbles");
        assertTrue(hasCorrectionBubbleWith("I go"), "should contain round 1 correction original text");
        assertTrue(hasCorrectionBubbleWith("I think is very hard"), "should contain round 2 correction original text");

        assertEquals(6, countCorrectionSidebarItems(), "sidebar should have 6 correction items (3 from round 1, 2 from round 2, 1 from round 3)");

        page.waitForSelector("[data-testid='correction-toggle']");
        page.locator("[data-testid='correction-toggle']").click();
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"correction-sidebar\"]').getAttribute('aria-expanded') === 'true'");
        page.locator("[data-testid='correction-sidebar-close']").click();
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"correction-sidebar\"]').getAttribute('aria-expanded') === 'false'");

        endSession();
        takeScreenshot("report");

        assertTrue(isReportModalVisible(), "report modal should be visible");
        String reportText = getReportModalText();
        assertTrue(reportText.contains("7"), "report should show fluency score");
        assertTrue(reportText.contains("Overall Assessment"), "report should show assessment section");
        assertTrue(reportText.contains("Key Takeaway"), "report should show key takeaway section");

        verifyH2Data(sid);
    }

    private void verifyH2Data(String sid) {
        Session session = sessionRepository.findById(sid).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session.getStatus(), "session should be completed");
        assertEquals(AgentMode.WORKPLACE_STANDUP, session.getMode());
        assertNotNull(session.getEndTime(), "session should have endTime");

        List<Message> messages = messageRepository.findBySessionIdOrderByCreateTimeAsc(sid);
        assertEquals(6, messages.size(), "should have 6 messages (3 USER + 3 AGENT)");
        assertEquals(MessageRole.USER, messages.get(0).getRole());
        assertEquals(MessageRole.AGENT, messages.get(1).getRole());
        assertEquals(MessageRole.USER, messages.get(2).getRole());
        assertEquals(MessageRole.AGENT, messages.get(3).getRole());
        assertEquals(MessageRole.USER, messages.get(4).getRole());
        assertEquals(MessageRole.AGENT, messages.get(5).getRole());

        List<ErrorRecord> errors = errorRecordRepository.findBySessionId(sid);
        assertTrue(errors.size() >= 3, "should have at least 3 error records");
        for (ErrorRecord e : errors) {
            assertNotNull(e.getType(), "error record type should not be null");
            assertNotNull(e.getOriginalText(), "error record original text should not be null");
            assertNotNull(e.getCorrectedText(), "error record corrected text should not be null");
        }

        SessionReport report = sessionReportRepository.findBySessionId(sid).orElseThrow();
        assertNotNull(report.getSummary(), "report summary should not be null");

        //not implement yet
        //assertTrue(report.getFluencyScore() > 0, "fluency score should be positive");
        //assertNotNull(report.getKeyTakeaway(), "key takeaway should not be null");
    }
}
