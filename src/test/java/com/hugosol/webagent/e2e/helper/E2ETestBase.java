package com.hugosol.webagent.e2e.helper;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hugosol.webagent.model.*;
import com.hugosol.webagent.repository.*;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class E2ETestBase {

    protected static final int WIREMOCK_PORT = 19090;
    protected static WireMockServer wireMockServer;
    protected static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @LocalServerPort
    protected int serverPort;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected MessageRepository messageRepository;

    @Autowired
    protected ErrorRecordRepository errorRecordRepository;

    @Autowired
    protected SessionReportRepository sessionReportRepository;

    private int turnNumber = 0;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
        wireMockServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMockServer::stop));
    }

    @BeforeAll
    static void launchBrowser() {
        browser = Playwright.create().chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
    }

    @BeforeEach
    void setUp() {
        turnNumber = 0;
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(390, 844)
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                        "Version/17.0 Mobile/15E148 Safari/604.1")
                .setIsMobile(true)
                .setDeviceScaleFactor(3));
        page = context.newPage();
        page.navigate("http://localhost:" + serverPort);

        WireMockStubs.registerAllStubs(wireMockServer);
    }

    @AfterEach
    void tearDown() {
        takeScreenshot("final");
        if (context != null) context.close();
    }

    protected void takeScreenshot(String name) {
        if (page == null) return;
        try {
            Path dir = Paths.get("target/e2e-screenshots");
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(getClass().getSimpleName() + "_" + name + ".png")));
        } catch (Exception ignored) {
        }
    }

    protected String sessionId() {
        return (String) page.evaluate("localStorage.getItem('sessionId')");
    }

    protected void startSession(String scenario, String persona) {
        page.locator("#scenarioSelect").selectOption(scenario);
        page.locator("#personaSelect").selectOption(persona);
        page.locator("#startBtn").click();
        page.waitForFunction(
                "() => !document.getElementById('textInputBar').classList.contains('hidden')");
    }

    protected void sendMessage(String text) {
        turnNumber++;
        page.locator("#textInput").fill(text);
        page.locator("#sendTextBtn").click();
    }

    protected void waitForAgentResponse() {
        page.waitForFunction("() => !document.getElementById('textInput').disabled");
        page.waitForFunction(
                "expected => document.querySelectorAll('.correction-bubble').length >= expected",
                turnNumber);
    }

    protected void endSession() {
        page.locator("#endBtn").click();
        page.waitForFunction(
                "() => !document.getElementById('reportModal').classList.contains('hidden')");
    }

    protected void reloadPage() {
        page.reload();
        page.waitForSelector(".message.user");
    }

    protected int countUserMessages() {
        return page.locator(".message.user").count();
    }

    protected int countAgentMessages() {
        return page.locator(".message.agent").count();
    }

    protected int countCorrectionBubbles() {
        return page.locator(".message.correction-bubble").count();
    }

    protected boolean hasCorrectionBubbleWith(String containedText) {
        return page.locator(".correction-bubble .content-text")
                .filter(new Locator.FilterOptions().setHasText(containedText))
                .count() > 0;
    }

    protected int countCorrectionSidebarItems() {
        return page.locator(".correction-item").count();
    }

    protected boolean isReportModalVisible() {
        return page.locator("#reportModal").isVisible();
    }

    protected String getReportModalText() {
        return page.locator("#reportContent").innerText();
    }
}
