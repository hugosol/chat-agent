package com.hugosol.webagent.e2e;

import com.hugosol.webagent.e2e.helper.E2ETestBase;
import com.hugosol.webagent.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnglishCoachMemoryIT extends E2ETestBase {

    @BeforeEach
    void cleanMemoryTable() {
        userMemoryRepository.deleteAll();
    }

    @Test
    void memoryGeneratedAtSessionEndAndMergedOnNextSession() {
        // --- Session 1 ---
        startSession("WORKPLACE_STANDUP");

        String sid1 = sessionId();
        assertNotNull(sid1, "sessionId should be in localStorage");

        sendMessage("Yesterday I discussed the login module with my team.");
        waitForAgentResponse();
        takeScreenshot("memory-s1-round1");

        sendMessage("We also talked about my upcoming trip to Japan.");
        waitForAgentResponse();
        takeScreenshot("memory-s1-round2");

        assertEquals(2, countUserMessages());

        endSession();
        takeScreenshot("memory-s1-report");

        // Verify report modal contains topic summary
        String reportText = getReportModalText();
        assertTrue(reportText.contains("Topic Summary"), "report should show topic summary section");
        assertTrue(reportText.contains("Java developer"), "report should show topic content");

        // Verify Session1 H2 data
        Session session1 = sessionRepository.findById(sid1).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session1.getStatus());

        // Verify UserMemory v1 was generated
        var topicV1 = userMemoryRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.TOPIC_SUMMARY, AgentMode.WORKPLACE_STANDUP);
        assertTrue(topicV1.isPresent(), "topic memory should be generated");
        assertEquals(1, topicV1.get().getVersion());
        assertTrue(topicV1.get().getContent().contains("login module"));

        var profileV1 = userMemoryRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.LEARNING_PROFILE, null);
        assertTrue(profileV1.isPresent(), "learning profile should be generated");
        assertEquals(1, profileV1.get().getVersion());

        // --- Session 2: use "New Session" button to restart ---
        page.locator("#newSessionBtn").click();
        page.waitForFunction(
                "() => !document.getElementById('textInputBar').classList.contains('hidden')");
        takeScreenshot("memory-s2-started");

        String sid2 = sessionId();
        assertNotNull(sid2);
        assertNotEquals(sid1, sid2);

        sendMessage("I'm back. Can we talk about travel vocabulary?");
        page.waitForFunction("() => !document.getElementById('textInput').disabled");
        takeScreenshot("memory-s2-round1");

        assertEquals(1, countUserMessages());

        endSession();
        takeScreenshot("memory-s2-report");

        // Verify Session2 H2 data
        Session session2 = sessionRepository.findById(sid2).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session2.getStatus());

        // Verify UserMemory merged: both types now have v2
        List<UserMemory> allTopic = userMemoryRepository.findByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.TOPIC_SUMMARY, AgentMode.WORKPLACE_STANDUP);
        assertEquals(2, allTopic.size(), "should have 2 topic memory rows (v1 + v2)");

        UserMemory topicV1row = allTopic.stream().filter(m -> m.getVersion() == 1).findFirst().orElseThrow();
        UserMemory topicV2row = allTopic.stream().filter(m -> m.getVersion() == 2).findFirst().orElseThrow();
        assertNotEquals(topicV1row.getContent(), topicV2row.getContent(),
                "merged topic memory should differ from v1");

        List<UserMemory> allProfile = userMemoryRepository.findByUserIdAndTypeAndModeOrderByVersionDesc(
                "anonymous", MemoryType.LEARNING_PROFILE, null);
        assertEquals(2, allProfile.size(), "should have 2 learning profile rows (v1 + v2)");

        UserMemory profileV1row = allProfile.stream().filter(m -> m.getVersion() == 1).findFirst().orElseThrow();
        UserMemory profileV2row = allProfile.stream().filter(m -> m.getVersion() == 2).findFirst().orElseThrow();
        assertNotEquals(profileV1row.getContent(), profileV2row.getContent(),
                "merged learning profile should differ from v1");
    }
}
