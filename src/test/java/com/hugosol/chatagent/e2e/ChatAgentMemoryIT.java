package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAgentMemoryIT extends E2ETestBase {

    @BeforeEach
    void cleanMemoryTable() {
        userLearningProfileRepository.deleteAll();
    }

    @Test
    void memoryGeneratedAtSessionEndAndMergedOnNextSession() {
        // --- Session 1 ---
        startSession(AgentMode.WORKPLACE_STANDUP.name());

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
        assertTrue(reportText.contains("Overall Assessment"), "report should show overall assessment section");
        assertTrue(reportText.contains("Good job"), "report should show assessment content");

        // Verify Session1 H2 data
        Session session1 = sessionRepository.findById(sid1).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session1.getStatus());

        // Verify UserLearningProfile v1 was generated (only LEARNING_PROFILE remains)
        var profileV1 = userLearningProfileRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                DEFAULT_USER_ID, LearningType.LEARNING_PROFILE, null);
        assertTrue(profileV1.isPresent(), "learning profile should be generated");
        assertEquals(1, profileV1.get().getVersion());

        // --- Session 2: close report modal and start new session ---
        page.locator("[data-testid=\"report-close-btn\"]").click();
        page.waitForFunction(
                "() => { const el = document.querySelector('[data-testid=\"report-modal\"]'); return !el || el.getAttribute('aria-hidden') === 'true'; }");
        startSession(AgentMode.WORKPLACE_STANDUP.name());

        String sid2 = sessionId();
        assertNotNull(sid2);
        assertNotEquals(sid1, sid2);

        sendMessage("I'm back. Can we talk about travel vocabulary?");
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
        takeScreenshot("memory-s2-round1");

        assertEquals(1, countUserMessages());

        endSession();
        takeScreenshot("memory-s2-report");

        // Verify Session2 H2 data
        Session session2 = sessionRepository.findById(sid2).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, session2.getStatus());

        // Verify LEARNING_PROFILE v2 was written as new version
        List<UserLearningProfile> allProfile = userLearningProfileRepository.findByUserIdAndTypeAndModeOrderByVersionDesc(
                DEFAULT_USER_ID, LearningType.LEARNING_PROFILE, null);
        assertEquals(2, allProfile.size(), "should have 2 learning profile rows (v1 + v2)");

        UserLearningProfile profileV1row = allProfile.stream().filter(m -> m.getVersion() == 1).findFirst().orElseThrow();
        UserLearningProfile profileV2row = allProfile.stream().filter(m -> m.getVersion() == 2).findFirst().orElseThrow();
        assertNotEquals(profileV1row.getContent(), profileV2row.getContent(),
                "merged learning profile should differ from v1");
    }
}
