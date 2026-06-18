package com.hugosol.chatagent.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class CardEnhanceIT extends E2ETestBase {

    @Autowired
    private CardEnhancementRepository cardEnhancementRepository;

    @Autowired
    private WatchedMovieRepository watchedMovieRepository;

    @Autowired
    private SubtitleLineRepository subtitleLineRepository;

    private String deckId;

    @BeforeEach
    void setupData() {
        cardEnhancementRepository.deleteAll();
        subtitleLineRepository.deleteAll();
        watchedMovieRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        reviewLogRepository.deleteAll();

        // Stub Wiktionary: sections API → find Etymology + Derived terms indices
        stubFor(get(urlPathEqualTo("/w/api.php"))
                .withQueryParam("action", equalTo("parse"))
                .withQueryParam("prop", equalTo("sections"))
                .withQueryParam("page", equalTo("dream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"parse":{"title":"dream","sections":[
                                  {"toclevel":1,"level":"2","line":"English","index":"1"},
                                  {"toclevel":2,"level":"3","line":"Etymology","index":"2"},
                                  {"toclevel":3,"level":"4","line":"Noun","index":"3"},
                                  {"toclevel":4,"level":"5","line":"Derived terms","index":"4"}
                                ]}}
                                """)));

        // Stub Wiktionary: etymology wikitext → extract source
        stubFor(get(urlPathEqualTo("/w/api.php"))
                .withQueryParam("action", equalTo("parse"))
                .withQueryParam("prop", equalTo("wikitext"))
                .withQueryParam("section", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"parse":{"title":"dream","wikitext":{"*":"===Etymology===\
                From {{inh|en|enm|dreme}}, from {{inh|en|ang|dr\u0113am|t=music, joy}}."}}}
                                """)));

        // Stub Wiktionary: derived terms wikitext
        stubFor(get(urlPathEqualTo("/w/api.php"))
                .withQueryParam("action", equalTo("parse"))
                .withQueryParam("prop", equalTo("wikitext"))
                .withQueryParam("section", equalTo("4"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"parse":{"title":"dream","wikitext":{"*":"=====Derived terms=====\
                {{col|en|dreamer|dreamful|dreamless|dreamlike|dreamland}}"}}}
                                """)));

        // Stub DeepSeek scene summary (non-streaming JSON response)
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing("场景摘要")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"test-id\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"科幻场景中关于梦境和意识的对话。\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}")));

        // Import a movie with subtitles
        WatchedMovie movie = new WatchedMovie(DEFAULT_USER_ID, "tt001", "Test Movie", 2020, SubtitleStatus.PENDING);
        watchedMovieRepository.save(movie);

        // Populate SubtitleLine data
        subtitleLineRepository.save(new SubtitleLine("tt001", "Test Movie", "00:01:00,000", "00:01:02,500",
                "I dream of electric sheep.", "i dream of electric sheep", 1));
        subtitleLineRepository.save(new SubtitleLine("tt001", "Test Movie", "00:02:00,000", "00:02:02,500",
                "Do androids dream?", "do androids dream", 2));

        // Create a tag and card for review
        Tag deck = new Tag("test-deck", DEFAULT_USER_ID);
        deck.setType("deck");
        deck = tagRepository.save(deck);
        deckId = deck.getId();

        Card card = new Card(DEFAULT_USER_ID, "dream", "梦");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setDue(Instant.now().minus(1, ChronoUnit.HOURS));
        card.setReps(0);
        card.setLapses(0);
        card.setTags(Set.of(deck));
        cardRepository.save(card);
    }

    @Test
    void fullEnhanceFlow_showsMovieQuoteAndEtymology() {
        // Navigate to review page
        page.navigate("http://localhost:" + serverPort + "/review/index.html");
        page.waitForSelector("[data-testid=\"deck-select\"]");

        // Select deck and mode
        page.locator("[data-testid=\"deck-select\"]").selectOption(deckId);
        page.waitForFunction(
                "() => { var btn = document.querySelector(\"[data-testid='start-btn']\"); return btn && !btn.disabled; }");

        // Start review
        page.locator("[data-testid=\"start-btn\"]").click();
        page.waitForSelector("[data-testid=\"card-front\"]");

        // Flip card
        page.locator("[data-testid=\"flip-card-btn\"]").click();
        page.waitForSelector("[data-testid=\"card-back\"]");

        // Verify definition zone is shown
        assertThat(page.locator("[data-testid=\"definition-zone\"]").isVisible()).isTrue();

        // Verify Card Enhance button is visible (no pre-existing enhancement)
        assertThat(page.locator("[data-testid=\"card-enhance-btn\"]").isVisible()).isTrue();

        // Click Card Enhance
        page.locator("[data-testid=\"card-enhance-btn\"]").click();

        // Wait for confirm dialog and click confirm
        page.waitForSelector("[data-testid=\"enhance-confirm-dialog\"]");
        page.locator("[data-testid=\"enhance-confirm-ok\"]").click();

        // Wait for loading overlay to appear then disappear
        page.waitForSelector("[data-testid=\"enhance-loading\"]");
        page.waitForSelector("[data-testid=\"movie-quote-zone\"]");

        // Verify movie quote zone content
        assertThat(page.locator("[data-testid=\"movie-quote-zone\"]").innerText())
                .contains("dream");

        // Verify scene summary
        assertThat(page.locator("[data-testid=\"scene-summary\"]").innerText())
                .contains("科幻场景");

        // Verify etymology zone (source + derived terms)
        String etymologyText = page.locator("[data-testid=\"etymology-zone\"]").innerText();
        assertThat(etymologyText).contains("Old English");
        assertThat(etymologyText).contains("dreamer");

        // Verify CardEnhancement rows saved
        var enhancements = cardEnhancementRepository.findAll();
        assertThat(enhancements).hasSize(2);
        assertThat(enhancements).extracting(CardEnhancement::getStatus)
                .containsOnly(EnhancementStatus.SUCCESS);
    }
}
