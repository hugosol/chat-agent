package com.hugosol.webagent.e2e;

import com.hugosol.webagent.e2e.helper.E2ETestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnglishCoachResumeIT extends E2ETestBase {

    @Test
    void sessionResumesAfterPageReload() {
        startSession("WORKPLACE_STANDUP");

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage after session start");
        assertTrue(sid.length() > 0, "sessionId should be non-empty");

        sendMessage("Yesterday I go to office and meet my colleague. He say we need finish the project this week.");
        waitForAgentResponse();
        takeScreenshot("resume_before_reload");

        sendMessage("I think is very hard.");
        waitForAgentResponse();
        takeScreenshot("resume_before_reload2");

        int userMessagesBefore = countUserMessages();
        int agentMessagesBefore = countAgentMessages();
        int correctionBubblesBefore = countCorrectionBubbles();
        int sidebarItemsBefore = countCorrectionSidebarItems();

        assertTrue(userMessagesBefore >= 2, "should have at least 2 user messages before reload");
        assertTrue(agentMessagesBefore >= 2, "should have at least 2 agent messages before reload");
        assertTrue(correctionBubblesBefore >= 2, "should have at least 2 correction bubbles before reload");
        assertTrue(sidebarItemsBefore >= 2, "should have at least 2 sidebar items before reload");

        reloadPage();
        takeScreenshot("resume_after_reload");

        assertEquals(sid, sessionId(), "sessionId should be the same after resume");

        assertEquals(userMessagesBefore, countUserMessages(),
                "user messages should be restored after reload");
        assertTrue(countAgentMessages() >= agentMessagesBefore,
                "agent messages should be restored after reload");
        assertEquals(correctionBubblesBefore, countCorrectionBubbles(),
                "correction bubbles should be restored after reload");
        assertEquals(sidebarItemsBefore, countCorrectionSidebarItems(),
                "sidebar items should be restored after reload");

        String localStorageSessionId = (String) page.evaluate(
                "localStorage.getItem('sessionId')");
        assertEquals(sid, localStorageSessionId,
                "localStorage should contain the sessionId");
    }
}
