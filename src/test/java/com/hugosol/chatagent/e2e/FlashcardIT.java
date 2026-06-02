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
        page.locator("[data-testid=\"flashcard-toggle\"]").click();
        page.waitForSelector("[data-testid=\"flashcard-panel\"][aria-expanded=\"true\"]");

        page.locator("[data-testid=\"flashcard-front\"]").fill("yesterday");
        page.locator("[data-testid=\"flashcard-continue\"]").click();
        page.waitForSelector("[data-testid=\"flashcard-stage2\"]:not([aria-hidden=\"true\"])");

        page.locator("[data-testid=\"flashcard-back\"]").fill("昨天");

        page.locator("[data-testid=\"flashcard-tag-input\"]").click();
        page.waitForSelector("[data-testid=\"flashcard-tag-suggestions\"]:not([aria-hidden=\"true\"])");
        page.locator("[data-testid=\"tag-suggestion-item\"]").first().click();
        page.waitForSelector("[data-testid=\"flashcard-chip\"]");

        page.locator("[data-testid=\"flashcard-tag-input\"]").click();
        page.waitForSelector("[data-testid=\"flashcard-tag-suggestions\"]:not([aria-hidden=\"true\"])");
        page.locator("[data-testid=\"tag-suggestion-item\"]").last().click();

        var chips = page.locator("[data-testid=\"flashcard-chip\"]");
        assertThat(chips.count()).isEqualTo(2);

        page.locator("[data-testid=\"flashcard-save\"]").click();

        page.waitForSelector("[data-testid=\"flashcard-toast\"]:not([aria-hidden=\"true\"])");

        page.waitForFunction(
                "() => { const el = document.querySelector('[data-testid=\"flashcard-panel\"]'); return el && el.getAttribute('aria-expanded') === 'false'; }");

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
