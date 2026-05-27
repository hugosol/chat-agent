package com.hugosol.webagent.e2e.helper;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WireMockStubs {

    private static final String SCENARIO_CONV = "conversation-rounds";
    private static final String SCENARIO_CORR = "correction-rounds";
    private static final String SCENARIO_MEM_TOPIC = "memory-topic-rounds";
    private static final String SCENARIO_MEM_PROFILE = "memory-profile-rounds";
    private static final String SCENARIO_CUE_ENTRY = "memory-cue-entry-rounds";

    private static final String KEYWORD_CORRECTION = "Correction prompt:";
    private static final String KEYWORD_REPORT = "Report prompt.";
    private static final String KEYWORD_MEM_TOPIC = "E2E_MARKER_MEMORY_TOPIC";
    private static final String KEYWORD_MEM_PROFILE = "E2E_MARKER_MEMORY_PROFILE";
    private static final String KEYWORD_MEM_CUE_SPLIT = "E2E_MARKER_MEMORY_CUE_SPLIT";
    private static final String KEYWORD_MEM_CUE_ENTRY = "E2E_MARKER_MEMORY_CUE_ENTRY";


    public static void registerAllStubs(WireMockServer wireMock) {
        configureFor("localhost", wireMock.port());
        wireMock.resetAll();

        registerConversationStubs();
        registerCorrectionStubs();
        registerReportStub();
        registerMemoryTopicStubs();
        registerMemoryProfileStubs();
        registerMemoryCueStubs();
    }

    public static void registerDailyTalkStubs(WireMockServer wireMock) {
        configureFor("localhost", wireMock.port());
        wireMock.resetAll();

        registerDailyConversationStubs();
        registerDailyCorrectionStubs();
        registerDailyReportStub();
        registerMemoryTopicStubs();
        registerMemoryProfileStubs();
        registerMemoryCueStubs();
    }

    private static void registerConversationStubs() {
        String convKeyword = "E2E_MARKER_WORKPLACE_STANDUP";

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/conv-round-1.txt")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/conv-round-2.txt")))
                .willSetStateTo("round-3"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/conv-round-3.txt")))
                .willSetStateTo("round-4"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-4")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: [DONE]\n\n")));
    }

    private static void registerDailyConversationStubs() {
        String convKeyword = "E2E_MARKER_DAILY_TALK";

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/daily-conv-round-1.txt")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/daily-conv-round-2.txt")))
                .willSetStateTo("round-3"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(loadResource("wiremock/daily-conv-round-3.txt")))
                .willSetStateTo("round-4"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(convKeyword)))
                .inScenario(SCENARIO_CONV)
                .whenScenarioStateIs("round-4")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: [DONE]\n\n")));
    }

    private static void registerDailyCorrectionStubs() {
        String corrKeyword = KEYWORD_CORRECTION;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario("daily-correction-rounds")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/daily-correction-round-1.json")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario("daily-correction-rounds")
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/daily-correction-round-2.json")))
                .willSetStateTo("round-3"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario("daily-correction-rounds")
                .whenScenarioStateIs("round-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/daily-correction-round-3.json")))
                .willSetStateTo("round-4"));
    }

    private static void registerDailyReportStub() {
        String reportKeyword = KEYWORD_REPORT;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(reportKeyword)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/daily-report.json"))));
    }

    private static void registerCorrectionStubs() {
        String corrKeyword = KEYWORD_CORRECTION;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario(SCENARIO_CORR)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/correction-round-1.json")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario(SCENARIO_CORR)
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/correction-round-2.json")))
                .willSetStateTo("round-3"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(corrKeyword)))
                .inScenario(SCENARIO_CORR)
                .whenScenarioStateIs("round-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/correction-round-3.json")))
                .willSetStateTo("round-4"));
    }

    private static void registerReportStub() {
        String reportKeyword = KEYWORD_REPORT;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(reportKeyword)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/report.json"))));
    }

    private static void registerMemoryTopicStubs() {
        String keyword = KEYWORD_MEM_TOPIC;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(keyword)))
                .inScenario(SCENARIO_MEM_TOPIC)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-topic-init.json")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(keyword)))
                .inScenario(SCENARIO_MEM_TOPIC)
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-topic-merge.json")))
                .willSetStateTo("round-3"));
    }

    private static void registerMemoryProfileStubs() {
        String keyword = KEYWORD_MEM_PROFILE;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(keyword)))
                .inScenario(SCENARIO_MEM_PROFILE)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-profile-init.json")))
                .willSetStateTo("round-2"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(keyword)))
                .inScenario(SCENARIO_MEM_PROFILE)
                .whenScenarioStateIs("round-2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-profile-merge.json")))
                .willSetStateTo("round-3"));
    }

    private static void registerMemoryCueStubs() {
        String splitKeyword = KEYWORD_MEM_CUE_SPLIT;
        String entryKeyword = KEYWORD_MEM_CUE_ENTRY;

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(splitKeyword)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-cue-split-two-switch.json"))));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(entryKeyword)))
                .inScenario(SCENARIO_CUE_ENTRY)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-cue-entry-success.json")))
                .willSetStateTo("segment-1"));

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(entryKeyword)))
                .inScenario(SCENARIO_CUE_ENTRY)
                .whenScenarioStateIs("segment-1")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/memory-cue-entry-seg2.json"))));
    }

    private static String loadResource(String path) {
        try (InputStream is = WireMockStubs.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }
}
