package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsPageIT extends E2ETestBase {

    @BeforeEach
    void cleanUp() {
        userPreferencesRepository.deleteAll();
    }

    @Test
    void settingsPageFullFlow() throws Exception {
        page.navigate("http://localhost:" + serverPort + "/settings/index.html");

        page.waitForSelector("[data-testid='settings-learningPreset']");
        page.waitForSelector("[data-testid='settings-desiredRetention']");
        page.waitForSelector("[data-testid='settings-enableFuzz']");

        page.locator("[data-testid='settings-learningPreset']").selectOption("快速毕业");
        var learningInput = page.locator("[data-testid='settings-learningSteps']");
        assertThat(learningInput.inputValue()).isEqualTo("30m");
        assertThat(learningInput.getAttribute("readonly")).isNotNull();

        page.locator("[data-testid='settings-desiredRetention']").fill("0.85");

        page.locator("[data-testid='settings-enableFuzz']").click(
                new com.microsoft.playwright.Locator.ClickOptions().setForce(true)
        );

        page.locator("[data-testid='settings-saveBtn']").click();

        page.waitForSelector("[data-testid='toast']");
        assertThat(page.locator("[data-testid='toast']").textContent()).contains("设置已保存");

        page.waitForFunction(
            "() => !document.querySelector('[data-testid=\"toast\"]')");

        page.reload();
        page.waitForSelector("[data-testid='settings-learningPreset']");

        page.waitForFunction(
            "() => document.querySelector('[data-testid=\"settings-desiredRetention\"]')?.value === '0.85'"
        );

        var reloadedLearningPreset = page.locator("[data-testid='settings-learningPreset']");
        assertThat(reloadedLearningPreset.inputValue()).isEqualTo("快速毕业");
    }

    @Test
    void settingsPageNavLinkPresent() throws Exception {
        page.navigate("http://localhost:" + serverPort + "/settings/index.html");
        page.waitForSelector("[data-testid='nav-menu-btn']");

        page.locator("[data-testid='nav-menu-btn']").click();
        page.waitForSelector("[data-testid='nav-sidebar'][aria-expanded='true']");

        var settingsLink = page.locator("[data-testid='nav-settings']");
        assertThat(settingsLink.isVisible()).isTrue();
        assertThat(settingsLink.getAttribute("data-active")).isEqualTo("true");
    }
}
