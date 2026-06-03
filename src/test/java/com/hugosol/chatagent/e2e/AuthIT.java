package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.ViewportSize;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("auth")
class AuthIT extends E2ETestBase {

    private String baseUrl() {
        return "http://localhost:" + serverPort;
    }

    private void goToLoginPage() {
        context.clearCookies();
        page.navigate(baseUrl() + "/login/main.html");
    }

    @Test
    void auth_loginSuccess() {
        goToLoginPage();

        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();

        page.waitForSelector("[data-testid='nav-menu-btn']");
        assertThat(page.url()).contains("/index.html");

        takeScreenshot("logged-in");
    }

    @Test
    void auth_loginFailure() {
        goToLoginPage();

        page.fill("#username", "admin");
        page.fill("#password", "wrong");
        page.locator("button:has-text('Log in')").click();

        page.waitForSelector("#errorMsg:not(.hidden)");

        takeScreenshot("login-failed");
    }

    @Test
    void auth_unauthenticatedRedirect() {
        page.navigate(baseUrl() + "/index.html");
        page.waitForSelector("#loginForm");

        takeScreenshot("redirected-to-login");
    }

    @Test
    void auth_logout() {
        goToLoginPage();

        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("before-logout");

        page.locator("[data-testid='nav-logout-form']").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        takeScreenshot("after-logout");
    }

    @Test
    void auth_rememberMe() {
        goToLoginPage();

        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("#rememberMe").check();
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("before-reopen");

        String storageState = context.storageState();
        context.close();

        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(390, 844))
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                        "Version/17.0 Mobile/15E148 Safari/604.1")
                .setIsMobile(true)
                .setDeviceScaleFactor(3)
                .setStorageState(storageState);

        BrowserContext newContext = browser.newContext(options);
        try {
            Page newPage = newContext.newPage();
            newPage.navigate(baseUrl() + "/index.html");
            newPage.waitForSelector("[data-testid='nav-menu-btn']");
        } finally {
            newContext.close();
        }

        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(390, 844))
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                        "Version/17.0 Mobile/15E148 Safari/604.1")
                .setIsMobile(true)
                .setDeviceScaleFactor(3));
        page = context.newPage();
    }
}
