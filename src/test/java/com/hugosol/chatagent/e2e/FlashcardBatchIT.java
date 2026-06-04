package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.BatchOperationLog;
import com.hugosol.chatagent.model.BatchOperationStatus;
import com.hugosol.chatagent.model.BatchOperationType;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.BatchOperationLogRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardBatchIT extends E2ETestBase {

    @Autowired
    private BatchOperationLogRepository batchLogRepository;

    @BeforeEach
    void cleanUp() {
        batchLogRepository.deleteAll(batchLogRepository.findAll());
        cardRepository.deleteAll(cardRepository.findAll());
        tagRepository.deleteAll(tagRepository.findAll());
    }

    @Test
    void fullExportImportRoundTrip() {
        Tag deckTag = new Tag("My Deck", DEFAULT_USER_ID);
        deckTag.setType("deck");
        deckTag = tagRepository.save(deckTag);

        Card card1 = new Card(DEFAULT_USER_ID, "hello", "Hello world");
        card1.setStability(3.0);
        card1.setDifficulty(0.3);
        card1.setCardState(2);
        card1.setDue(Instant.parse("2024-06-15T10:00:00Z"));
        card1.setReps(5);
        card1.setLapses(1);
        card1.setLastReview(Instant.parse("2024-06-10T10:00:00Z"));
        card1.setFirstReviewDate(Instant.parse("2024-06-01T00:00:00Z"));
        card1.setTags(Set.of(deckTag));
        card1 = cardRepository.save(card1);

        Card card2 = new Card(DEFAULT_USER_ID, "goodbye", "Goodbye world");
        card2.setStability(2.5);
        card2.setDifficulty(0.1);
        card2.setCardState(0);
        card2.setDue(Instant.parse("2024-06-16T10:00:00Z"));
        card2.setReps(3);
        card2.setLapses(0);
        card2.setLastReview(null);
        card2.setTags(Set.of(deckTag));
        cardRepository.save(card2);

        page.navigate("http://localhost:" + serverPort + "/manage/index.html");
        page.waitForSelector("[data-testid='tab-cards']");
        page.locator("[data-testid='tab-cards']").click();
        page.waitForSelector("[data-testid='card-search']");

        page.locator("[data-testid='batch-dropdown-btn']").click();
        page.waitForSelector("[data-testid='batch-option']");
        page.locator("[data-testid='batch-option']").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText("导出")).click();

        page.waitForSelector("[data-testid='batch-tag-select']");
        page.locator("[data-testid='batch-tag-select']").selectOption("My Deck");
        page.waitForSelector("[data-testid='batch-export-btn']");
        assertThat(page.locator("[data-testid='batch-export-btn']").isEnabled()).isTrue();

        page.locator("[data-testid='batch-export-btn']").click();
        page.waitForTimeout(2000);

        assertThat(batchLogRepository.findAll()).hasSize(1);
        BatchOperationLog exportLog = batchLogRepository.findAll().get(0);
        assertThat(exportLog.getOperationType()).isEqualTo(BatchOperationType.EXPORT);
        assertThat(exportLog.getStatus()).isEqualTo(BatchOperationStatus.SUCCESS);

        cardRepository.delete(card1);
        cardRepository.delete(card2);
        cardRepository.flush();
        assertThat(cardRepository.findAll()).hasSize(0);

        page.locator("[data-testid='batch-dropdown-btn']").click();
        page.waitForSelector("[data-testid='batch-option']");
        page.locator("[data-testid='batch-option']").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText("导入")).click();

        page.waitForSelector("[data-testid='batch-tag-select']");
        page.locator("[data-testid='batch-tag-select']").selectOption("My Deck");
        page.waitForSelector("[data-testid='batch-file-input']");

        page.locator("[data-testid='batch-file-input']").setInputFiles(
                new com.microsoft.playwright.options.FilePayload("cards.csv", "text/csv",
                        ("front,back,stability,difficulty,cardState,due,reps,lapses,lastReview,firstReviewDate\n" +
                         "hello,Hello world,3.0,0.3,Review,2024-06-15T10:00:00Z,5,1,2024-06-10T10:00:00Z,2024-06-01\n" +
                         "goodbye,Goodbye world,2.5,0.1,New,2024-06-16T10:00:00Z,3,0,,\n").getBytes()));

        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='batch-import-btn']\"); return btn && !btn.disabled; }");

        page.locator("[data-testid='batch-import-btn']").click();

        page.waitForSelector("[data-testid='batch-close-btn']", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(20000));

        page.locator("[data-testid='batch-close-btn']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        var cards = cardRepository.findAll();
        assertThat(cards).hasSize(2);

        var helloCard = cards.stream().filter(c -> c.getFront().equals("hello")).findFirst().orElseThrow();
        assertThat(helloCard.getStability()).isEqualTo(3.0);
        assertThat(helloCard.getDifficulty()).isEqualTo(0.3);
        assertThat(helloCard.getCardState()).isEqualTo(2);
        assertThat(helloCard.getReps()).isEqualTo(5);
        assertThat(helloCard.getLapses()).isEqualTo(1);
        assertThat(helloCard.getFirstReviewDate()).isNotNull();

        var goodbyeCard = cards.stream().filter(c -> c.getFront().equals("goodbye")).findFirst().orElseThrow();
        assertThat(goodbyeCard.getStability()).isEqualTo(2.5);

        List<BatchOperationLog> logs = batchLogRepository.findAll();
        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(BatchOperationLog::getOperationType)
                .containsExactlyInAnyOrder(BatchOperationType.EXPORT, BatchOperationType.IMPORT);
        assertThat(logs).allMatch(log -> log.getStatus() == BatchOperationStatus.SUCCESS);

        takeScreenshot("roundtrip-complete");
    }
}
