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
        var lastToastMsg = new AtomicReference<String>();

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
        page.waitForSelector("[data-testid='tab-cards']");
        page.waitForSelector("[data-testid='tab-tags']");

        takeScreenshot("step1c-manage-page");

        // === Step 2: Tag creation ===
        page.locator("[data-testid='tab-tags']").click();

        page.waitForSelector("button:has-text('创建标签')");
        page.waitForTimeout(100);

        page.locator("button:has-text('创建标签')").click();
        page.waitForSelector("[data-testid='modal-overlay']");

        page.locator("#newTagDeck").check();
        page.locator("#newTagName").fill("Daily English");
        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        page.waitForSelector("[data-testid='tag-table'] tr[data-name='Daily English']");
        assertThat(page.locator("[data-testid='tag-table'] tr[data-name='Daily English'] input[type='checkbox']").isChecked()).isTrue();

        page.locator("button:has-text('创建标签')").click();
        page.waitForSelector("[data-testid='modal-overlay']");

        page.locator("#newTagName").fill("verb");
        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        page.waitForSelector("[data-testid='tag-table'] tr[data-name='verb']");
        assertThat(page.locator("[data-testid='tag-table'] tr[data-name='verb'] input[type='checkbox']").isChecked()).isFalse();

        var tags = tagRepository.findAll();
        assertThat(tags).hasSize(2);
        assertThat(tags).extracting(Tag::getType).containsExactlyInAnyOrder("deck", null);

        takeScreenshot("step2-tags-created");

        // === Step 3: Card creation ===
        page.locator("[data-testid='tab-cards']").click();

        page.waitForSelector("[data-testid='card-search']");
        page.waitForSelector("[data-testid='empty-state']");
        assertThat(page.locator("[data-testid='empty-state']").textContent()).contains("暂无卡片");

        page.locator("button:has-text('+ 创建卡片')").click();
        page.waitForSelector("[data-testid='modal-overlay']");

        page.locator("[data-testid='card-form-front']").fill("yesterday");
        page.locator("[data-testid='card-form-back']").fill("昨天");

        page.locator("[data-testid='chip-input-field']").fill("Daily");
        page.waitForSelector("[data-testid='chip-suggestion']:has-text('Daily English')");
        page.locator("[data-testid='chip-suggestion']:has-text('Daily English')").click();

        page.locator("[data-testid='chip-input-field']").fill("verb");
        page.waitForSelector("[data-testid='chip-suggestion']:has-text('verb')");
        page.locator("[data-testid='chip-suggestion']:has-text('verb')").click();

        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        page.waitForSelector("[data-testid='card-block']");
        assertThat(page.locator("[data-testid='card-front']").textContent()).contains("yesterday");
        assertThat(page.locator("[data-testid='card-back']").textContent()).contains("昨天");

        var tagChips = page.locator("[data-testid='card-tag-chip']");
        assertThat(tagChips.count()).isEqualTo(2);
        var chipTexts = page.locator("[data-testid='card-tag-chip']").allTextContents();
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
        page.locator("[data-testid='btn-edit-card']").click();
        page.waitForSelector("[data-testid='modal-overlay']");

        assertThat(page.locator("[data-testid='card-form-front']").inputValue()).isEqualTo("yesterday");
        assertThat(page.locator("[data-testid='card-form-back']").inputValue()).isEqualTo("昨天");

        page.locator("[data-testid='card-form-front']").fill("Yesterday");

        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        page.waitForSelector("[data-testid='card-front']");
        page.waitForFunction(
                "() => { var el = document.querySelector(\"[data-testid='card-front']\"); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");
        assertThat(page.locator("[data-testid='card-front']").textContent()).contains("Yesterday");

        takeScreenshot("step4-card-edited");

        // === Step 5: Search ===
        page.locator("[data-testid='card-search']").fill("yesterday");
        page.waitForFunction(
                "() => { var el = document.querySelector(\"[data-testid='card-front']\"); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");

        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(1);
        assertThat(page.locator("[data-testid='card-front']").textContent()).contains("Yesterday");

        page.locator("[data-testid='card-search']").fill("");
        page.waitForTimeout(400);

        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(1);

        takeScreenshot("step5-search");

        // === Step 6: Sort ===
        page.locator("[data-testid='sort-btn-name']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='sort-btn-name'][data-active='true']\") !== null");
        assertThat(page.locator("[data-testid='sort-btn-name'][data-active='true']").count()).isEqualTo(1);

        page.locator("[data-testid='sort-btn-name']").click();
        page.waitForTimeout(200);

        page.locator("[data-testid='sort-btn-time']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='sort-btn-time'][data-active='true']\") !== null");

        page.locator("[data-testid='sort-btn-time']").click();
        page.waitForTimeout(200);

        page.locator("[data-testid='sort-btn-name']").click();
        page.waitForTimeout(200);

        takeScreenshot("step6-sort");

        // === Step 7: Deck chip filtering ===
        Tag verbTag = tagRepository.findAll().stream()
                .filter(t -> "verb".equals(t.getName()))
                .findFirst().orElseThrow();
        Card cardNoDeck = new Card(DEFAULT_USER_ID, "hello", "你好");
        cardNoDeck.getTags().add(verbTag);
        cardRepository.save(cardNoDeck);

        page.locator("[data-testid='tab-tags']").click();
        page.waitForSelector("[data-testid='tag-table']");
        page.locator("[data-testid='tab-cards']").click();
        page.waitForSelector("[data-testid='card-block']");

        page.waitForSelector("[data-testid='deck-chip']:has-text('Daily English')");
        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(2);

        page.locator("[data-testid='deck-chip']:has-text('Daily English')").click();
        page.waitForFunction(
                "() => { var el = document.querySelector(\"[data-testid='card-front']\"); " +
                "return el && el.textContent && el.textContent.includes('Yesterday'); }");
        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(1);
        assertThat(page.locator("[data-testid='card-front']").textContent()).contains("Yesterday");

        page.locator("[data-testid='deck-chip']:has-text('Daily English')").click();
        page.waitForTimeout(400);
        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(2);

        takeScreenshot("step7-deck-filter");

        // === Step 8: Pagination ===
        for (int i = 0; i < 19; i++) {
            Card c = new Card(DEFAULT_USER_ID, "card" + String.format("%02d", i), "卡片" + i);
            cardRepository.save(c);
        }

        page.locator("[data-testid='tab-tags']").click();
        page.waitForSelector("[data-testid='tag-table']");
        page.locator("[data-testid='tab-cards']").click();
        page.waitForSelector("[data-testid='card-block']");

        page.waitForSelector("[data-testid='pagination']");
        var pageButtons = page.locator("[data-testid='pagination'] [data-testid='page-num']");
        assertThat(pageButtons.count()).isEqualTo(3);
        assertThat(page.locator("[data-testid='pagination'] [data-testid='page-num'][data-active='true']").textContent()).isEqualTo("1");

        page.locator("[data-testid='pagination'] [data-testid='page-num']").nth(1).click();
        page.waitForTimeout(500);
        assertThat(page.locator("[data-testid='pagination'] [data-testid='page-num'][data-active='true']").textContent()).isEqualTo("2");
        assertThat(page.locator("[data-testid='card-block']").count()).isGreaterThan(0);

        takeScreenshot("step8-pagination");

        var allCards = cardRepository.findAll();
        for (Card c : allCards) {
            if (!"Yesterday".equals(c.getFront())) {
                cardRepository.delete(c);
            }
        }

        page.locator("[data-testid='tab-tags']").click();
        page.waitForSelector("[data-testid='tag-table']");
        page.locator("[data-testid='tab-cards']").click();
        page.waitForSelector("[data-testid='card-block']");

        page.waitForSelector("[data-testid='card-block']");
        assertThat(page.locator("[data-testid='card-block']").count()).isEqualTo(1);

        // === Step 9: Card detail modal ===
        page.locator("[data-testid='card-block']").click();
        page.waitForSelector("[data-testid='modal-overlay'] .detail-item");

        var detailItems = page.locator("[data-testid='modal-overlay'] .detail-item");
        assertThat(detailItems.count()).isGreaterThanOrEqualTo(5);

        page.waitForFunction(
                "() => { var labels = document.querySelectorAll(\"[data-testid='modal-overlay'] .detail-item .detail-label\"); " +
                "for (var i = 0; i < labels.length; i++) { " +
                "if (labels[i].textContent === 'Front') { " +
                "var val = labels[i].nextElementSibling; " +
                "return val && val.textContent && val.textContent.includes('Yesterday'); " +
                "} } return false; }");
        assertThat(page.locator("[data-testid='modal-overlay'] .detail-item .detail-label:has-text('Front') + .detail-value").textContent())
                .contains("Yesterday");
        assertThat(page.locator("[data-testid='modal-overlay'] .detail-item .detail-label:has-text('Back') + .detail-value").textContent())
                .isEqualTo("昨天");
        assertThat(page.locator("[data-testid='modal-overlay'] .detail-item .detail-label:has-text('State') + .detail-value").textContent())
                .isEqualTo("New");

        takeScreenshot("step9-detail-modal");

        page.locator("[data-testid='modal-cancel']").click();
        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        // === Step 11: Tag inline edit ===
        page.locator("[data-testid='tab-tags']").click();
        page.waitForSelector("[data-testid='tag-table']");

        page.waitForSelector("[data-testid='tag-table'] tr[data-name='verb'] [data-testid='btn-edit-tag']");
        page.locator("[data-testid='tag-table'] tr[data-name='verb'] [data-testid='btn-edit-tag']").click();

        page.waitForSelector("[data-testid='edit-name-input']");
        assertThat(page.locator("[data-testid='edit-name-input']").inputValue()).isEqualTo("verb");

        page.locator("[data-testid='edit-name-input']").fill("verbs");
        page.locator("[data-testid='btn-save-tag']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='edit-name-input']\") === null");

        assertThat(page.locator("[data-testid='tag-table'] tr[data-name='verbs'] td:first-child").textContent()).isEqualTo("verbs");

        takeScreenshot("step11-tag-edited");

        // === Step 12: Orphan check (BEFORE card deletion, still on Tags tab from step 11) ===
        page.waitForSelector("[data-testid='tag-table'] tr[data-name='Daily English'] [data-testid='btn-delete-tag']");
        page.locator("[data-testid='tag-table'] tr[data-name='Daily English'] [data-testid='btn-delete-tag']").click();

        page.waitForSelector("[data-testid='modal-overlay']");
        page.locator("[data-testid='modal-save']").click();

        page.waitForSelector("[data-testid='toast']");
        lastToastMsg.set(page.locator("[data-testid='toast']").textContent());
        assertThat(lastToastMsg.get()).contains("张卡片将失去所有牌组");

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='modal-overlay']\") === null");

        assertThat(page.locator("[data-testid='tag-table'] tr[data-name='Daily English']").count()).isEqualTo(1);

        var tagsAfterOrphan = tagRepository.findAll();
        assertThat(tagsAfterOrphan).hasSize(2);
        assertThat(tagsAfterOrphan).extracting(Tag::getName)
                .contains("Daily English");

        takeScreenshot("step12-orphan-alert");

        // === Step 10: Card deletion ===
        page.locator("[data-testid='tab-cards']").click();
        page.waitForSelector("[data-testid='card-block']");

        page.waitForSelector("[data-testid='btn-delete-card']");
        page.locator("[data-testid='btn-delete-card']").click();

        page.waitForSelector("[data-testid='modal-overlay']");
        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='card-block']\") === null");

        var cardsAfterDelete = cardRepository.findAll();
        assertThat(cardsAfterDelete).isEmpty();

        takeScreenshot("step10-card-deleted");

        // === Step 13: Tag deletion success ===
        page.locator("[data-testid='tab-tags']").click();
        page.waitForSelector("[data-testid='tag-table']");

        page.waitForSelector("[data-testid='tag-table'] tr[data-name='verbs'] [data-testid='btn-delete-tag']");
        page.locator("[data-testid='tag-table'] tr[data-name='verbs'] [data-testid='btn-delete-tag']").click();

        page.waitForSelector("[data-testid='modal-overlay']");
        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='tag-table'] tr[data-name='verbs']\") === null");

        var tagsAfterVerbDelete = tagRepository.findAll();
        assertThat(tagsAfterVerbDelete).hasSize(1);

        page.waitForSelector("[data-testid='tag-table'] tr[data-name='Daily English'] [data-testid='btn-delete-tag']");
        page.locator("[data-testid='tag-table'] tr[data-name='Daily English'] [data-testid='btn-delete-tag']").click();

        page.waitForSelector("[data-testid='modal-overlay']");
        page.locator("[data-testid='modal-save']").click();

        page.waitForFunction(
                "() => document.querySelector(\"[data-testid='tag-table'] tr[data-name='Daily English']\") === null");

        var tagsAfterDeckDelete = tagRepository.findAll();
        assertThat(tagsAfterDeckDelete).isEmpty();

        page.waitForSelector("[data-testid='empty-state']");
        assertThat(page.locator("[data-testid='empty-state']").textContent()).contains("暂无标签");

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
