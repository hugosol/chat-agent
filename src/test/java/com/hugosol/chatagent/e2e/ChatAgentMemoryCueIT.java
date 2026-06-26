package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryAssertion;
import com.hugosol.chatagent.model.MemoryCue;
import com.hugosol.chatagent.model.MemoryCueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAgentMemoryCueIT extends E2ETestBase {

    @BeforeEach
    void cleanMemoryCueTable() {
        memoryCueRepository.deleteAll();
    }

    @Test
    void memoryCueGeneratedAtSessionEndWithTopicSwitch() throws InterruptedException {
        startSession(AgentMode.WORKPLACE_STANDUP.name());

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage");

        sendMessage("Today I worked on the login module.");
        waitForAgentResponse();
        takeScreenshot("memorycue-r1");

        sendMessage("We also discussed the new sprint planning process.");
        waitForAgentResponse();
        takeScreenshot("memorycue-r2");

        sendMessage("By the way, I'm planning a trip to Japan next month.");
        waitForAgentResponse();
        takeScreenshot("memorycue-r3");

        assertEquals(3, countUserMessages());

        endSession();
        takeScreenshot("memorycue-report");

        String reportText = getReportModalText();
        assertTrue(reportText.contains("Overall Assessment"), "report should show overall assessment section");

        // Wait for async MemoryCue + Assertion pipelines
        Thread.sleep(500);

        List<MemoryCue> cues = memoryCueRepository.findBySessionId(sid);
        assertFalse(cues.isEmpty(), "should have at least one memory cue record");
        List<MemoryCue> completedCues = cues.stream()
                .filter(c -> c.getStatus() == MemoryCueStatus.COMPLETED)
                .toList();
        assertFalse(completedCues.isEmpty(), "should have at least one COMPLETED cue");

        assertEquals(AgentMode.WORKPLACE_STANDUP, cues.get(0).getMode());
        assertEquals(DEFAULT_USER_ID, cues.get(0).getUserId());

        // Verify MemoryCue records have topics and summaries
        for (MemoryCue cue : completedCues) {
            assertNotNull(cue.getTopic(), "completed cue should have a topic");
            assertNotNull(cue.getSummary(), "completed cue should have a summary");
        }

        // Verify Assertion records also exist (parallel pipeline)
        List<MemoryAssertion> assertions = assertionRepository.findBySessionId(sid);
        assertFalse(assertions.isEmpty(), "should have at least one assertion record");
    }
}
