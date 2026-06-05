package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.UserRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.ViewportSize;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("auth")
class AuthIT extends E2ETestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

        page.locator("[data-testid='nav-user-btn'] form").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        takeScreenshot("after-logout");
    }

    @Test
    void auth_disabledUserCannotLogin() {
        User user = new User("blockme", passwordEncoder.encode("password123"));
        user.setEnabled(false);
        userRepository.save(user);

        goToLoginPage();

        page.fill("#username", "blockme");
        page.fill("#password", "password123");
        page.locator("button:has-text('Log in')").click();

        page.waitForSelector("#errorMsg:not(.hidden)");

        takeScreenshot("disabled-user-login-failed");
    }

    @Test
    void auth_userMeReturnsUsernameAndRole() {
        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async () => { const r = await fetch('/api/user/me'); return await r.json(); }");

        assertThat(result).isNotNull();
        var resultStr = result.toString();
        assertThat(resultStr).contains("admin");
        assertThat(resultStr).contains("true");
    }

    @Test
    void auth_changeOwnPasswordSuccess() {
        User user = new User("testuser", passwordEncoder.encode("oldpass12"));
        user.setEnabled(true);
        userRepository.save(user);

        goToLoginPage();
        page.fill("#username", "testuser");
        page.fill("#password", "oldpass12");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        page.evaluate(
                "async () => { await fetch('/api/user/password', { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ currentPassword: 'oldpass12', newPassword: 'newpass34' }) }); }");

        takeScreenshot("password-changed");

        page.locator("[data-testid='nav-user-btn'] form").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        page.fill("#username", "testuser");
        page.fill("#password", "oldpass12");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("#errorMsg:not(.hidden)");

        goToLoginPage();
        page.fill("#username", "testuser");
        page.fill("#password", "newpass34");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("new-password-works");
    }

    @Test
    void auth_adminListUsersExcludesAdmin() {
        User user2 = new User("listuser", passwordEncoder.encode("password123"));
        userRepository.save(user2);

        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async () => { const r = await fetch('/api/admin/users'); return await r.json(); }");

        var resultStr = result.toString();
        assertThat(resultStr).contains("listuser");
        assertThat(resultStr).doesNotContain("\"username\":\"admin\"");
    }

    @Test
    void auth_adminCreateUserSuccess() {
        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async () => { const r = await fetch('/api/admin/users', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ username: 'newuser', password: 'newuser123' }) }); return { status: r.status, body: await r.json() }; }");

        var resultStr = result.toString();
        assertThat(resultStr).contains("200");
        assertThat(resultStr).contains("newuser");

        page.locator("[data-testid='nav-user-btn'] form").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        page.fill("#username", "newuser");
        page.fill("#password", "newuser123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("new-user-can-login");
    }

    @Test
    void auth_regularUserCannotAccessAdminApi() {
        User user = new User("regular", passwordEncoder.encode("password123"));
        userRepository.save(user);

        goToLoginPage();
        page.fill("#username", "regular");
        page.fill("#password", "password123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async () => { const r = await fetch('/api/admin/users'); return r.status; }");

        assertThat(result).isEqualTo(403);
        takeScreenshot("regular-user-admin-api-403");
    }

    @Test
    void auth_adminDisableUserThenLoginFails() {
        User user2 = new User("disableme", passwordEncoder.encode("password123"));
        user2 = userRepository.save(user2);

        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async (userId) => { const r = await fetch('/api/admin/users/' + userId + '/enabled', { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ enabled: false }) }); return r.status; }",
                user2.getId());

        assertThat(result).isEqualTo(200);

        page.locator("[data-testid='nav-user-btn'] form").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        page.fill("#username", "disableme");
        page.fill("#password", "password123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("#errorMsg:not(.hidden)");

        takeScreenshot("disabled-user-login-blocked");
    }

    @Test
    void auth_adminCannotDisableSelf() {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();

        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object status = page.evaluate(
                "async (adminId) => { const r = await fetch('/api/admin/users/' + adminId + '/enabled', { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ enabled: false }) }); return r.status; }",
                adminUser.getId());

        assertThat(status).isEqualTo(400);

        takeScreenshot("admin-cannot-disable-self");
    }

    @Test
    void auth_adminResetUserPassword() {
        User user2 = new User("resetme", passwordEncoder.encode("oldpass12"));
        user2 = userRepository.save(user2);

        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object result = page.evaluate(
                "async (userId) => { const r = await fetch('/api/admin/users/' + userId + '/password', { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ adminPassword: 'admin123', newPassword: 'newpass34' }) }); return r.status; }",
                user2.getId());

        assertThat(result).isEqualTo(200);

        page.locator("[data-testid='nav-user-btn'] form").evaluate("form => form.submit()");
        page.waitForSelector("#loginForm");

        page.fill("#username", "resetme");
        page.fill("#password", "newpass34");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("reset-password-works");
    }

    @Test
    void auth_adminCannotResetAdminPassword() {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();

        goToLoginPage();
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.locator("button:has-text('Log in')").click();
        page.waitForSelector("[data-testid='nav-menu-btn']");

        Object status = page.evaluate(
                "async (adminId) => { const r = await fetch('/api/admin/users/' + adminId + '/password', { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ adminPassword: 'admin123', newPassword: 'newpass34' }) }); return r.status; }",
                adminUser.getId());

        assertThat(status).isEqualTo(400);

        takeScreenshot("admin-cannot-reset-admin-password");
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
