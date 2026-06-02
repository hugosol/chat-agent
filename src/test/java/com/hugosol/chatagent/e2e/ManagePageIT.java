package com.hugosol.chatagent.e2e;

import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ManagePageIT extends E2ETestBase {

    @BeforeEach
    void cleanUp() {
        cardRepository.deleteAll(cardRepository.findAll());
        tagRepository.deleteAll(tagRepository.findAll());
    }

    @Test
    void fullManageFlow_navTagCrudCardCrud_searchSortFilterPagination_detailModal_orphanAlert_delete() {
        var lastAlertMsg = new AtomicReference<String>();

        page.onDialog(dialog -> {
            if ("alert".equals(dialog.type())) {
                lastAlertMsg.set(dialog.message());
            }
            dialog.accept();
        });

        // === Step 1: nav sidebar on chat page ===
        takeScreenshot("step1a-chat-page");

        page.waitForSelector("[data-testid='nav-menu-btn']");
        page.locator("[data-testid='nav-menu-btn']").click();
        page.waitForSelector("[data-testid='nav-sidebar'][aria-expanded='true']");

        assertThat(page.locator("[data-testid='nav-link']").count()).isEqualTo(2);
        assertThat(page.locator("[data-testid='nav-link'][data-active='true']").textContent()).isEqualTo("💬 Chat");

        takeScreenshot("step1b-nav-open");

        page.locator("[data-testid='nav-sidebar-close']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='nav-sidebar']\").getAttribute('aria-expanded') !== 'true'");

        page.navigate("http://localhost:" + serverPort + "/manage/index.html");
        page.waitForSelector(".manage-layout");
        page.waitForSelector(".manage-tab-btn");

        takeScreenshot("step1c-manage-page");

        // === Step 2: Tag creation ===
        page.locator(".manage-tab-btn[data-tab='tags']").click();

        page.waitForSelector("#tagsTab:not(.hidden)");
        page.waitForSelector("#tagsTab .empty-state");
        assertThat(page.locator("#tagsTab .empty-state").textContent()).contains("暂无标签");

        page.locator("#tagsTab .empty-state button:has-text('创建标签')").click();
        page.waitForSelector(".modal");

        page.locator("#newTagDeck").check();
        page.locator("#newTagName").fill("Daily English");
        page.locator(".modal .btn-save").click();

        page.waitForFunction(
                "() => document.querySelector('.modal') === null");

        page.waitForSelector("#tagsTab .tag-table");
        assertThat(page.locator("#tagsTab tr[data-name='Daily English'] .tag-deck-cb").isChecked()).isTrue();

        page.locator("#tagsTab button:has-text('创建标签')").click();
        page.waitForSelector(".modal");

        page.locator("#newTagName").fill("verb");
        page.locator(".modal .btn-save").click();

        page.waitForFunction(
                "() => document.querySelector('.modal') === null");

        page.waitForSelector("#tagsTab tr[data-name='verb']");
        assertThat(page.locator("#tagsTab tr[data-name='verb'] .tag-deck-cb").isChecked()).isFalse();

        var tags = tagRepository.findAll();
        assertThat(tags).hasSize(2);
        assertThat(tags).extracting(Tag::getType).containsExactlyInAnyOrder("deck", null);

        takeScreenshot("step2-tags-created");

        // === Step 3: Card creation ===
        page.locator(".manage-tab-btn[data-tab='cards']").click();

        page.waitForSelector("#cardsTab:not(.hidden)");
        page.waitForSelector("#cardsTab .empty-state");
        assertThat(page.locator("#cardsTab .empty-state").textContent()).contains("暂无卡片");

        page.locator("#cardsTab .empty-state button:has-text('创建第一张卡片')").click();
        page.waitForSelector(".modal");

        assertThat(page.locator(".modal .tag-cb").count()).isEqualTo(2);

        page.locator("#createFront").fill("yesterday");
        page.locator("#createBack").fill("昨天");
        page.locator(".modal .tag-cb").nth(0).check();
        page.locator(".modal .tag-cb").nth(1).check();

        page.locator(".modal .btn-save").click();

        page.waitForFunction(
                "() => document.querySelector('.modal') === null");

        page.waitForSelector("#cardsTab .card-block");
        assertThat(page.locator("#cardsTab .card-front").textContent()).contains("yesterday");
        assertThat(page.locator("#cardsTab .card-back").textContent()).contains("昨天");

        var tagChips = page.locator("#cardsTab .card-tags .chip");
        assertThat(tagChips.count()).isEqualTo(2);
        var chipTexts = page.locator("#cardsTab .card-tags .chip").allTextContents();
        assertThat(chipTexts).anyMatch(t -> t.contains("Daily English"));
        assertThat(chipTexts).anyMatch(t -> t.contains("verb"));

        var cards = cardRepository.findAll();
        assertThat(cards).hasSize(1);
        Card card = cards.get(0);
        assertThat(card.getFront()).isEqualTo("yesterday");
        assertThat(card.getBack()).isEqualTo("昨天");
        assertThat(card.getUserId()).isEqualTo(DEFAULT_USER_ID);
        assertThat(card.getTags()).hasSize(2);

        takeScreenshot("step3-card-created");

        // === Step 4: Card edit ===
        page.locator("#cardsTab .btn-edit-card").click();
        page.waitForSelector("#editFront");

        assertThat(page.locator("#editFront").inputValue()).isEqualTo("yesterday");
        assertThat(page.locator("#editBack").inputValue()).isEqualTo("昨天");

        page.locator("#editFront").fill("Yesterday");

        page.locator(".modal .btn-save").click();

        page.waitForFunction(
                "() => document.querySelector('.modal') === null");

        page.waitForSelector("#cardsTab .card-front");
        page.waitForFunction(
                "() => { var el = document.querySelector('#cardsTab .card-front'); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");
        assertThat(page.locator("#cardsTab .card-front").textContent()).contains("Yesterday");

        takeScreenshot("step4-card-edited");

        // === Step 5: Search ===
        page.locator("#cardSearch").fill("yesterday");
        page.waitForFunction(
                "() => { var el = document.querySelector('#cardsTab .card-front'); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");

        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(1);
        assertThat(page.locator("#cardsTab .card-front").textContent()).contains("Yesterday");

        page.locator("#cardSearch").fill("");
        page.waitForTimeout(400);

        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(1);

        takeScreenshot("step5-search");

        // === Step 6: Sort ===
        page.locator(".sort-btn[data-sort='front,asc']").click();
        page.waitForFunction(
                "() => document.querySelector('.sort-btn[data-sort=\"front,asc\"].active') !== null");
        assertThat(page.locator(".sort-btn.active[data-sort='front,asc']").count()).isEqualTo(1);

        page.locator(".sort-btn[data-sort='front,asc']").click();
        page.waitForTimeout(200);

        page.locator(".sort-btn[data-sort='createTime,desc']").click();
        page.waitForFunction(
                "() => document.querySelector('.sort-btn[data-sort=\"createTime,desc\"].active') !== null");

        page.locator(".sort-btn[data-sort='createTime,desc']").click();
        page.waitForTimeout(200);

        page.locator(".sort-btn[data-sort='front,asc']").click();
        page.waitForTimeout(200);

        takeScreenshot("step6-sort");

        // === Step 7: Deck chip filtering ===
        Tag verbTag = tagRepository.findAll().stream()
                .filter(t -> "verb".equals(t.getName()))
                .findFirst().orElseThrow();
        Card cardNoDeck = new Card(DEFAULT_USER_ID, "hello", "你好");
        cardNoDeck.getTags().add(verbTag);
        cardRepository.save(cardNoDeck);

        page.locator(".manage-tab-btn[data-tab='tags']").click();
        page.waitForSelector("#tagsTab:not(.hidden)");
        page.locator(".manage-tab-btn[data-tab='cards']").click();
        page.waitForSelector("#cardsTab:not(.hidden)");

        page.waitForSelector(".deck-chip:has-text('Daily English')");
        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(2);

        page.locator(".deck-chip:has-text('Daily English')").click();
        page.waitForFunction(
                "() => { var el = document.querySelector('#cardsTab .card-front'); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");
        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(1);
        assertThat(page.locator("#cardsTab .card-front").textContent()).contains("Yesterday");

        page.locator(".deck-chip:has-text('Daily English')").click();
        page.waitForTimeout(400);
        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(2);

        takeScreenshot("step7-deck-filter");

        // === Step 8: Pagination ===
        for (int i = 0; i < 19; i++) {
            Card c = new Card(DEFAULT_USER_ID, "card" + String.format("%02d", i), "卡片" + i);
            cardRepository.save(c);
        }

        page.locator(".manage-tab-btn[data-tab='tags']").click();
        page.waitForSelector("#tagsTab:not(.hidden)");
        page.locator(".manage-tab-btn[data-tab='cards']").click();
        page.waitForSelector("#cardsTab:not(.hidden)");

        page.waitForSelector("#cardsTab .pagination");
        var pageButtons = page.locator("#cardsTab .pagination .page-num");
        assertThat(pageButtons.count()).isEqualTo(3);
        assertThat(page.locator("#cardsTab .pagination .page-num.active").textContent()).isEqualTo("1");

        page.locator("#cardsTab .pagination .page-num").nth(1).click();
        page.waitForTimeout(500);
        assertThat(page.locator("#cardsTab .pagination .page-num.active").textContent()).isEqualTo("2");
        assertThat(page.locator("#cardsTab .card-block").count()).isGreaterThan(0);

        takeScreenshot("step8-pagination");

        var allCards = cardRepository.findAll();
        for (Card c : allCards) {
            if (!"Yesterday".equals(c.getFront())) {
                cardRepository.delete(c);
            }
        }

        page.locator(".manage-tab-btn[data-tab='tags']").click();
        page.waitForSelector("#tagsTab:not(.hidden)");
        page.locator(".manage-tab-btn[data-tab='cards']").click();
        page.waitForSelector("#cardsTab:not(.hidden)");

        page.waitForSelector("#cardsTab .card-block");
        assertThat(page.locator("#cardsTab .card-block").count()).isEqualTo(1);

        // === Step 9: Card detail modal ===
        page.locator("#cardsTab .card-block").click();
        page.waitForSelector(".modal .detail-item");

        var detailItems = page.locator(".modal .detail-item");
        assertThat(detailItems.count()).isGreaterThanOrEqualTo(5);

        page.waitForFunction(
                "() => { var labels = document.querySelectorAll('.modal .detail-item .detail-label'); " +
                "for (var i = 0; i < labels.length; i++) { " +
                "if (labels[i].textContent === 'Front') { " +
                "var val = labels[i].nextElementSibling; " +
                "return val && val.textContent && val.textContent.includes('Yesterday'); " +
                "} } return false; }");
        assertThat(page.locator(".modal .detail-item .detail-label:has-text('Front') + .detail-value").textContent())
                .contains("Yesterday");
        assertThat(page.locator(".modal .detail-item .detail-label:has-text('Back') + .detail-value").textContent())
                .isEqualTo("昨天");
        assertThat(page.locator(".modal .detail-item .detail-label:has-text('State') + .detail-value").textContent())
                .isEqualTo("New");

        takeScreenshot("step9-detail-modal");

        page.locator(".modal .btn-cancel").click();
        page.waitForFunction(
                "() => document.querySelector('.modal') === null");

        // === Step 11: Tag inline edit ===
        page.locator(".manage-tab-btn[data-tab='tags']").click();
        page.waitForSelector("#tagsTab:not(.hidden)");

        page.waitForSelector("#tagsTab tr[data-name='verb'] .btn-edit");
        page.locator("#tagsTab tr[data-name='verb'] .btn-edit").click();

        page.waitForSelector("#tagsTab .edit-name-input");
        assertThat(page.locator("#tagsTab .edit-name-input").inputValue()).isEqualTo("verb");

        page.locator("#tagsTab .edit-name-input").fill("verbs");
        page.locator("#tagsTab .btn-save").click();

        page.waitForFunction(
                "() => document.querySelector('#tagsTab .edit-name-input') === null");

        assertThat(page.locator("#tagsTab tr[data-name='verbs'] td.tag-name").textContent()).isEqualTo("verbs");

        takeScreenshot("step11-tag-edited");

        // === Step 12: Orphan check (BEFORE card deletion, still on Tags tab from step 11) ===
        page.waitForSelector("#tagsTab tr[data-name='Daily English'] .btn-delete");
        page.locator("#tagsTab tr[data-name='Daily English'] .btn-delete").click();

        page.waitForTimeout(800);

        assertThat(lastAlertMsg.get()).contains("张卡片将失去所有牌组");
        assertThat(page.locator("#tagsTab tr[data-name='Daily English']").count()).isEqualTo(1);

        var tagsAfterOrphan = tagRepository.findAll();
        assertThat(tagsAfterOrphan).hasSize(2);
        assertThat(tagsAfterOrphan).extracting(Tag::getName)
                .contains("Daily English");

        takeScreenshot("step12-orphan-alert");

        // === Step 10: Card deletion ===
        page.locator(".manage-tab-btn[data-tab='cards']").click();
        page.waitForSelector("#cardsTab:not(.hidden)");

        page.waitForSelector("#cardsTab .btn-delete-card");
        page.locator("#cardsTab .btn-delete-card").click();

        page.waitForFunction(
                "() => document.querySelector('#cardsTab .card-block') === null");

        var cardsAfterDelete = cardRepository.findAll();
        assertThat(cardsAfterDelete).isEmpty();

        takeScreenshot("step10-card-deleted");

        // === Step 13: Tag deletion success ===
        page.locator(".manage-tab-btn[data-tab='tags']").click();
        page.waitForSelector("#tagsTab:not(.hidden)");

        page.waitForSelector("#tagsTab tr[data-name='verbs'] .btn-delete");
        page.locator("#tagsTab tr[data-name='verbs'] .btn-delete").click();

        page.waitForFunction(
                "() => document.querySelector(\"#tagsTab tr[data-name='verbs']\") === null");

        var tagsAfterVerbDelete = tagRepository.findAll();
        assertThat(tagsAfterVerbDelete).hasSize(1);

        page.waitForSelector("#tagsTab tr[data-name='Daily English'] .btn-delete");
        page.locator("#tagsTab tr[data-name='Daily English'] .btn-delete").click();

        page.waitForFunction(
                "() => document.querySelector(\"#tagsTab tr[data-name='Daily English']\") === null");

        var tagsAfterDeckDelete = tagRepository.findAll();
        assertThat(tagsAfterDeckDelete).isEmpty();

        page.waitForSelector("#tagsTab .empty-state");
        assertThat(page.locator("#tagsTab .empty-state").textContent()).contains("暂无标签");

        takeScreenshot("step13-tags-all-deleted");

        // === Step 14: nav sidebar on manage page ===
        page.locator("[data-testid='nav-menu-btn']").click();
        page.waitForSelector("[data-testid='nav-sidebar'][aria-expanded='true']");

        assertThat(page.locator("[data-testid='nav-link'][data-active='true']").textContent()).isEqualTo("📋 Manage");
        assertThat(page.locator("[data-testid='nav-link']").count()).isEqualTo(2);

        takeScreenshot("step14-nav-manage");

        page.locator("[data-testid='nav-sidebar-close']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='nav-sidebar']\").getAttribute('aria-expanded') !== 'true'");

        page.navigate("http://localhost:" + serverPort + "/");
        page.waitForSelector("[data-testid='nav-menu-btn']");

        takeScreenshot("step14-back-to-chat");
    }
}
