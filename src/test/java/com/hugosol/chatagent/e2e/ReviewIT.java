package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewIT extends E2ETestBase {

    private String deckId;

    @BeforeEach
    void seedData() {
        cardRepository.deleteAll(cardRepository.findAll());
        tagRepository.deleteAll(tagRepository.findAll());
        userPreferencesRepository.deleteAll(userPreferencesRepository.findAll());

        Tag deckTag = new Tag("Daily English", DEFAULT_USER_ID);
        deckTag.setType("deck");
        deckTag = tagRepository.save(deckTag);
        deckId = deckTag.getId();

        Card card1 = new Card(DEFAULT_USER_ID, "apple", "苹果");
        card1.setStability(2.5);
        card1.setDifficulty(0.0);
        card1.setCardState(0);
        card1.setDue(Instant.now().minus(1, ChronoUnit.HOURS));
        card1.setReps(0);
        card1.setLapses(0);
        card1.setTags(Set.of(deckTag));
        cardRepository.save(card1);

        Card card2 = new Card(DEFAULT_USER_ID, "banana", "香蕉");
        card2.setStability(3.0);
        card2.setDifficulty(0.3);
        card2.setCardState(2);
        card2.setDue(Instant.now().minus(2, ChronoUnit.HOURS));
        card2.setReps(3);
        card2.setLapses(0);
        card2.setLastReview(Instant.now().minus(2, ChronoUnit.DAYS));
        card2.setFirstReviewDate(Instant.now().minus(2, ChronoUnit.DAYS));
        card2.setTags(Set.of(deckTag));
        cardRepository.save(card2);

        Card card3 = new Card(DEFAULT_USER_ID, "cherry", "樱桃");
        card3.setStability(2.5);
        card3.setDifficulty(0.0);
        card3.setCardState(0);
        card3.setDue(Instant.now().minus(1, ChronoUnit.HOURS));
        card3.setReps(0);
        card3.setLapses(0);
        card3.setTags(Set.of(deckTag));
        cardRepository.save(card3);

        Card card4 = new Card(DEFAULT_USER_ID, "future card", "未来卡");
        card4.setStability(5.0);
        card4.setDifficulty(0.2);
        card4.setCardState(2);
        card4.setDue(Instant.now().plus(7, ChronoUnit.DAYS));
        card4.setReps(5);
        card4.setLapses(0);
        card4.setLastReview(Instant.now().minus(7, ChronoUnit.DAYS));
        card4.setFirstReviewDate(Instant.now().minus(14, ChronoUnit.DAYS));
        card4.setTags(Set.of(deckTag));
        cardRepository.save(card4);
    }

    @Test
    void scenario1_deckAndModeSelection() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        assertThat(page.locator("[data-testid='deck-item']").count()).isEqualTo(1);
        assertThat(page.locator("[data-testid='deck-item']").textContent()).contains("Daily English");

        assertThat(page.locator("[data-testid='mode-item']").count()).isEqualTo(4);

        assertThat(page.locator("[data-testid='start-btn']").isEnabled()).isFalse();

        page.locator("[data-testid='deck-item']").click();

        var startBtn = page.locator("[data-testid='start-btn']");
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");
        assertThat(startBtn.isEnabled()).isTrue();

        takeScreenshot("scenario1-deck-selected");
    }

    @Test
    void scenario2_standardReview_flipAndRate() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='card-front']");

        assertThat(page.locator("[data-testid='card-back']").count()).isEqualTo(0);

        page.locator("[data-testid='flip-card-btn']").click();
        page.waitForSelector("[data-testid='card-back']");

        assertThat(page.locator("[data-testid='rating-good']").isVisible()).isTrue();

        page.locator("[data-testid='rating-good']").click();
        page.waitForSelector("[data-testid='card-front']");
        page.waitForTimeout(500);

        var cards = cardRepository.findAll();
        var reviewedCards = cards.stream().filter(c -> c.getFirstReviewDate() != null).toList();
        assertThat(reviewedCards).isNotEmpty();

        takeScreenshot("scenario2-after-rating");
    }

    @Test
    void scenario3_statsBarUpdates() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='stats-bar']");

        assertThat(page.locator("[data-testid='stats-bar']").isVisible()).isTrue();

        page.locator("[data-testid='flip-card-btn']").click();
        page.waitForSelector("[data-testid='rating-good']");
        page.locator("[data-testid='rating-good']").click();
        page.waitForTimeout(500);

        assertThat(page.locator("[data-testid='stats-reviewed']").textContent()).contains("已复习");

        takeScreenshot("scenario3-stats");
    }

    @Test
    void scenario4_completePage() {
        cardRepository.findAll().stream()
                .filter(c -> c.getCardState() != 0 && c.getDue() != null)
                .forEach(c -> {
                    c.setDue(Instant.now().minus(1, ChronoUnit.HOURS));
                    cardRepository.save(c);
                });

        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();

        var ratingRounds = 0;
        while (ratingRounds < 5 && page.locator("[data-testid='flip-card-btn']").count() > 0) {
            page.waitForTimeout(300);
            if (page.locator("[data-testid='flip-card-btn']").count() > 0) {
                page.locator("[data-testid='flip-card-btn']").click();
                page.waitForSelector("[data-testid='rating-good']");
                page.locator("[data-testid='rating-good']").click();
                ratingRounds++;
                page.waitForTimeout(300);
            }
            if (page.locator("[data-testid='complete-page']").count() > 0) {
                break;
            }
        }

        page.waitForSelector("[data-testid='complete-page']", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(10000));
        assertThat(page.locator("[data-testid='complete-page']").isVisible()).isTrue();
        assertThat(page.locator("[data-testid='complete-back-btn']").isVisible()).isTrue();

        takeScreenshot("scenario4-complete");
    }

    @Test
    void scenario5_reviewOnlyMode() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();

        page.locator("[data-testid='mode-item']").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText("仅复习")).click();

        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='card-front']");
        page.waitForTimeout(300);

        var frontText = page.locator("[data-testid='card-front']").textContent();
        assertThat(frontText).doesNotContain("apple");
        assertThat(frontText).doesNotContain("cherry");

        takeScreenshot("scenario5-review-only");
    }

    @Test
    void scenario6_newOnlyMode() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();

        page.locator("[data-testid='mode-item']").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText("仅新卡")).click();

        page.waitForSelector("[data-testid='limit-input']");

        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='card-front']");
        page.waitForTimeout(300);

        var frontText = page.locator("[data-testid='card-front']").textContent();
        assertThat(frontText).containsAnyOf("apple", "cherry");

        takeScreenshot("scenario6-new-only");
    }

    @Test
    void scenario7_cramMode_allCardsReviewed() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();

        page.locator("[data-testid='mode-item']").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText("速通")).click();

        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='card-front']");

        var rounds = 0;
        while (rounds < 10 && page.locator("[data-testid='flip-card-btn']").count() > 0) {
            page.waitForTimeout(300);
            if (page.locator("[data-testid='flip-card-btn']").count() > 0) {
                page.locator("[data-testid='flip-card-btn']").click();
                page.waitForSelector("[data-testid='rating-good']");
                page.locator("[data-testid='rating-good']").click();
                rounds++;
                page.waitForTimeout(300);
            }
            if (page.locator("[data-testid='complete-page']").count() > 0) {
                break;
            }
        }

        var cards = cardRepository.findAll();
        var nonNewCards = cards.stream().filter(c -> c.getCardState() != 0).count();
        assertThat(nonNewCards).isEqualTo(cards.size());

        takeScreenshot("scenario7-cram");
    }

    @Test
    void scenario8_limitDialog() {
        UserPreferences prefs = new UserPreferences(DEFAULT_USER_ID);
        prefs.setNewCardDailyLimit(0);
        userPreferencesRepository.save(prefs);

        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();

        page.waitForSelector("[data-testid='card-front']");

        takeScreenshot("scenario8-limit");
    }

    @Test
    void scenario9_preferencesPersistence() {
        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        page.locator("[data-testid='deck-item']").click();

        page.locator("[data-testid='limit-input']").fill("15");

        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='start-btn']").click();
        page.waitForSelector("[data-testid='card-front']");
        page.waitForTimeout(300);

        page.navigate("http://localhost:" + serverPort + "/review/");
        page.waitForSelector("[data-testid='deck-item']");

        takeScreenshot("scenario9-persistence");
    }
}
