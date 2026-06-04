package com.hugosol.chatagent.e2e.helper;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.*;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
public abstract class E2ETestBase {

    protected static final String DEFAULT_USER_ID = "anonymous";

    protected static final int WIREMOCK_PORT = 19090;
    protected static WireMockServer wireMockServer;
    protected static Browser browser;
    private static final String runTimestamp;

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

    @Autowired
    protected UserLearningProfileRepository userLearningProfileRepository;

    @Autowired
    protected MemoryCueRepository memoryCueRepository;

    @Autowired
    protected CardRepository cardRepository;

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected UserPreferencesRepository userPreferencesRepository;

    private int turnNumber = 0;
    private String currentTestMethodName;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
        wireMockServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMockServer::stop));

        var now = LocalDateTime.now();
        String prefix = now.format(DateTimeFormatter.ofPattern("MM-dd-HHmm"));
        String suffix;
        Path dir;
        do {
            suffix = ThreadLocalRandom.current().ints(4, 'a', 'z' + 1)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
            dir = Paths.get("target/e2e-screenshots", prefix + "-" + suffix);
        } while (Files.exists(dir));
        runTimestamp = prefix + "-" + suffix;
        housekeepScreenshots();
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
    void setUp(TestInfo testInfo) {
        currentTestMethodName = testInfo.getTestMethod()
                .map(method -> method.getName())
                .orElse("unknown");
        turnNumber = 0;
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(390, 844)
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                        "Version/17.0 Mobile/15E148 Safari/604.1")
                .setIsMobile(true)
                .setDeviceScaleFactor(3));
        page = context.newPage();
        page.setDefaultTimeout(20_000);
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
            String className = this.getClass().getSimpleName();
            Path dir = Paths.get("target/e2e-screenshots", runTimestamp, className);
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(currentTestMethodName + "-" + name + ".png")));
        } catch (Exception ignored) {
        }
    }

    protected String sessionId() {
        return (String) page.evaluate("localStorage.getItem('sessionId')");
    }

    protected void startSession(String mode) {
        page.locator("[data-testid=\"mode-select\"]").selectOption(mode);
        page.locator("[data-testid=\"start-btn\"]").click();
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"end-btn\"]') && !document.querySelector('[data-testid=\"end-btn\"]').disabled");
    }

    protected void sendMessage(String text) {
        turnNumber++;
        page.locator("[data-testid=\"text-input\"]").fill(text);
        page.locator("[data-testid=\"send-btn\"]").click();
    }

    protected void waitForAgentResponse() {
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
        page.waitForFunction(
                "expected => document.querySelectorAll('[data-testid=\"correction-bubble\"]').length >= expected",
                turnNumber);
    }

    protected void endSession() {
        page.locator("[data-testid=\"end-btn\"]").click();
        page.waitForFunction(
                "() => document.querySelector('[data-testid=\"report-modal\"]') && document.querySelector('[data-testid=\"report-modal\"]').getAttribute('aria-hidden') !== 'true'");
    }

    protected void reloadPage() {
        page.reload();
        page.waitForSelector("[data-testid=\"message\"][data-role=\"user\"]");
    }

    protected int countUserMessages() {
        return page.locator("[data-testid=\"message\"][data-role=\"user\"]").count();
    }

    protected int countAgentMessages() {
        return page.locator("[data-testid=\"message\"][data-role=\"agent\"]").count();
    }

    protected int countCorrectionBubbles() {
        return page.locator("[data-testid=\"correction-bubble\"]").count();
    }

    protected boolean hasCorrectionBubbleWith(String containedText) {
        return page.locator("[data-testid=\"correction-bubble\"] [data-testid=\"message-content\"]")
                .filter(new Locator.FilterOptions().setHasText(containedText))
                .count() > 0;
    }

    protected int countCorrectionSidebarItems() {
        return page.locator("[data-testid=\"correction-item\"]").count();
    }

    protected boolean isReportModalVisible() {
        return page.locator("[data-testid=\"report-modal\"]").isVisible();
    }

    protected String getReportModalText() {
        return page.locator("[data-testid=\"report-content\"]").innerText();
    }

    private static void housekeepScreenshots() {
        Path screenshotsDir = Paths.get("target/e2e-screenshots");
        if (!Files.exists(screenshotsDir)) return;

        Pattern pattern = Pattern.compile("^(\\d{2})-(\\d{2})-(\\d{4})-[a-z]{4}$");
        var cutoff = LocalDateTime.now().minusHours(24);
        var today = LocalDate.now();

        try (var dirs = Files.list(screenshotsDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                var matcher = pattern.matcher(dir.getFileName().toString());
                if (!matcher.matches()) return;

                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                String time = matcher.group(3);
                int hour = Integer.parseInt(time.substring(0, 2));
                int minute = Integer.parseInt(time.substring(2));

                int year = today.getYear();
                if (month > today.getMonthValue()
                        || (month == today.getMonthValue() && day > today.getDayOfMonth())) {
                    year--;
                }
                var ts = LocalDateTime.of(year, month, day, hour, minute);

                if (ts.isBefore(cutoff)) {
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                                });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }
}
