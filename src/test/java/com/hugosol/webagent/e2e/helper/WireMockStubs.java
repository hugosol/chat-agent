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

    public static void registerAllStubs(WireMockServer wireMock) {
        configureFor("localhost", wireMock.port());
        wireMock.resetAll();

        registerConversationStubs();
        registerCorrectionStubs();
        registerReportStub();
    }

    private static void registerConversationStubs() {
        String convKeyword = "English conversation partner";

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
    }

    private static void registerCorrectionStubs() {
        String corrKeyword = "Correction prompt:";

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
        String reportKeyword = "Report prompt.";

        stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing(reportKeyword)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadResource("wiremock/report.json"))));
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
