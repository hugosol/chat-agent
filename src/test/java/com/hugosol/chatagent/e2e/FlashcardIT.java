package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardIT extends E2ETestBase {

    @BeforeEach
    void setupTags() {
        cardRepository.deleteAll(cardRepository.findAll());
        tagRepository.deleteAll(tagRepository.findAll());

        Tag deckTag = new Tag("daily", DEFAULT_USER_ID);
        deckTag.setType("deck");
        tagRepository.save(deckTag);

        Tag normalTag = new Tag("time", DEFAULT_USER_ID);
        tagRepository.save(normalTag);
    }

    @Test
    void fullFlashcardCreateFlow_persistsCardAndTags() {
        page.locator("#flashcardToggle").click();
        page.waitForSelector("#flashcardPanel:not(.collapsed)");

        page.locator("#flashcardFront").fill("yesterday");
        page.locator("#flashcardContinue").click();
        page.waitForSelector("#flashcardStage2:not(.hidden)");

        page.locator("#flashcardBack").fill("昨天");

        page.locator("#flashcardTagInput").click();
        page.waitForSelector("#flashcardTagSuggestions:not(.hidden)");
        page.locator(".tag-suggestion-item").first().click();
        page.waitForSelector(".flashcard-chip");

        page.locator("#flashcardTagInput").click();
        page.waitForSelector("#flashcardTagSuggestions:not(.hidden)");
        page.locator(".tag-suggestion-item").last().click();

        var chips = page.locator(".flashcard-chip");
        assertThat(chips.count()).isEqualTo(2);

        page.locator("#flashcardSave").click();

        page.waitForSelector("#flashcardToast:not(.hidden)");

        page.waitForFunction(
                "() => document.getElementById('flashcardPanel').classList.contains('collapsed')");

        var cards = cardRepository.findAll();
        assertThat(cards).hasSize(1);
        Card card = cards.get(0);
        assertThat(card.getFront()).isEqualTo("yesterday");
        assertThat(card.getBack()).isEqualTo("昨天");
        assertThat(card.getUserId()).isEqualTo(DEFAULT_USER_ID);
        assertThat(card.getStability()).isEqualTo(2.5);
        assertThat(card.getDifficulty()).isEqualTo(0.0);
        assertThat(card.getCardState()).isEqualTo(0);
        assertThat(card.getReps()).isEqualTo(0);
        assertThat(card.getLapses()).isEqualTo(0);
        assertThat(card.getDue()).isNotNull();
        assertThat(card.getLastReview()).isNull();

        assertThat(card.getTags()).hasSize(2);
        assertThat(card.getTags()).extracting(t -> t.getName())
                .containsExactlyInAnyOrder("daily", "time");
    }
}
