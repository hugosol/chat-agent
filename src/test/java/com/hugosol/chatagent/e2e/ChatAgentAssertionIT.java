package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryAssertion;
import com.hugosol.chatagent.model.MemoryCue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAgentAssertionIT extends E2ETestBase {

    @BeforeEach
    void cleanAssertionTables() {
        lineageRepository.deleteAll();
        assertionRepository.deleteAll();
        memoryCueRepository.deleteAll();
    }

    @Test
    void singleSessionGeneratesAssertions() throws InterruptedException {
        startSession(AgentMode.WORKPLACE_STANDUP.name());

        String sid = sessionId();
        assertNotNull(sid, "sessionId should be in localStorage");

        sendMessage("Today I worked on the login module.");
        waitForAgentResponse();

        sendMessage("I go to the store yesterday to buy some milk.");
        waitForAgentResponse();

        sendMessage("By the way, I have many sheeps in my hometown.");
        waitForAgentResponse();

        assertEquals(3, countUserMessages());

        endSession();

        String reportText = getReportModalText();
        assertTrue(reportText.contains("Overall Assessment"), "report should show overall assessment");

        // Wait for async assertion pipeline
        Thread.sleep(1000);

        List<MemoryAssertion> assertions = assertionRepository.findBySessionId(sid);
        assertFalse(assertions.isEmpty(), "should have at least one assertion");
        assertTrue(assertions.stream().allMatch(MemoryAssertion::isEnabled),
                "all assertions should be enabled");

        for (MemoryAssertion a : assertions) {
            assertNotNull(a.getTopic(), "topic should not be null");
            assertNotNull(a.getState(), "state should not be null");
            assertEquals(AgentMode.WORKPLACE_STANDUP, a.getMode());
            assertEquals(DEFAULT_USER_ID, a.getUserId());
            assertEquals(sid, a.getSessionId());
        }

        // Verify MemoryCue records are also created
        List<MemoryCue> cues = memoryCueRepository.findBySessionId(sid);
        assertFalse(cues.isEmpty(), "should have at least one memory cue record");
        for (MemoryCue cue : cues) {
            assertEquals(AgentMode.WORKPLACE_STANDUP, cue.getMode());
            assertEquals(DEFAULT_USER_ID, cue.getUserId());
        }
    }

    @Test
    void japaneseModeSkipsAssertions() throws InterruptedException {
        startSession(AgentMode.JAPANESE_BUSINESS.name());

        String sid = sessionId();
        assertNotNull(sid);

        sendMessage("今日の会議の準備をしました。");
        waitForAgentResponseNoCorrection();

        sendMessage("プレゼンの資料も確認しました。");
        waitForAgentResponseNoCorrection();

        assertEquals(2, countUserMessages());

        endSession();

        String reportText = getReportModalText();
        assertTrue(reportText.contains("Overall Assessment") || reportText.contains("評価"),
                "report should be visible");

        Thread.sleep(500);

        List<MemoryAssertion> assertions = assertionRepository.findBySessionId(sid);
        assertTrue(assertions.isEmpty(), "Japanese mode should not generate assertions");

        List<MemoryCue> cues = memoryCueRepository.findBySessionId(sid);
        assertTrue(cues.isEmpty(), "Japanese mode should not generate memory cues");
    }
}
