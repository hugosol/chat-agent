package com.hugosol.webagent.e2e;

import com.hugosol.webagent.e2e.helper.E2ETestBase;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnglishCoachMemoryCueIT extends E2ETestBase {

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
        assertTrue(reportText.contains("Topic Summary"), "report should show topic summary section");

        Thread.sleep(500);

        List<MemoryCue> cues = memoryCueRepository.findBySessionId(sid);
        assertEquals(2, cues.size(), "should have 2 memory cue records for 2 segments");

        List<MemoryCue> completed = cues.stream()
                .filter(c -> c.getStatus() == MemoryCueStatus.COMPLETED)
                .toList();
        assertEquals(2, completed.size(), "both records should be COMPLETED");

        assertEquals(AgentMode.WORKPLACE_STANDUP, cues.get(0).getMode());
        assertEquals(DEFAULT_USER_ID, cues.get(0).getUserId());

        assertNotEquals(cues.get(0).getTopic(), cues.get(1).getTopic(),
                "two segments should have different topics");
        assertNotNull(cues.get(0).getSummary(), "segment 0 should have a summary");
        assertNotNull(cues.get(1).getSummary(), "segment 1 should have a summary");
        assertFalse(cues.get(0).getTags().isEmpty(), "segment 0 should have tags");
        assertFalse(cues.get(1).getTags().isEmpty(), "segment 1 should have tags");
    }

    @Test
    void shouldConsolidateTagsAfterSessionEnd() throws InterruptedException {
        startSession(AgentMode.WORKPLACE_STANDUP.name());

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage");

        sendMessage("Today I worked on the login module.");
        waitForAgentResponse();
        takeScreenshot("consolidation-r1");

        sendMessage("We also discussed the new sprint planning.");
        waitForAgentResponse();
        takeScreenshot("consolidation-r2");

        sendMessage("By the way, I got a new spaniel puppy yesterday.");
        waitForAgentResponse();
        takeScreenshot("consolidation-r3");

        assertEquals(3, countUserMessages());

        endSession();
        takeScreenshot("consolidation-report");

        String reportText = getReportModalText();
        assertTrue(reportText.contains("Topic Summary"));

        Thread.sleep(500);

        List<MemoryCue> cues = memoryCueRepository.findBySessionId(sid);
        assertEquals(2, cues.size(), "should have 2 memory cue records");

        List<MemoryCue> completed = cues.stream()
                .filter(c -> c.getStatus() == MemoryCueStatus.COMPLETED)
                .toList();
        assertEquals(2, completed.size(), "both records should be COMPLETED");

        List<String> seg1Tags = cues.get(0).getTags();
        assertFalse(seg1Tags.contains("job"), "job should be consolidated to work");
        assertTrue(seg1Tags.contains("work"), "work should remain as canonical");

        List<String> seg2Tags = cues.get(1).getTags();
        assertFalse(seg2Tags.contains("spaniel"), "spaniel should be consolidated to dog");
        assertFalse(seg2Tags.contains("poodle"), "poodle should be consolidated to dog");
        assertTrue(seg2Tags.contains("dog"), "dog should remain as canonical");
    }
}
