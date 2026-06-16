package com.hugosol.chatagent.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class CardEnhanceIT extends E2ETestBase {

    @Autowired
    private CardEnhancementRepository cardEnhancementRepository;

    @Autowired
    private WatchedMovieRepository watchedMovieRepository;

    @Autowired
    private SubtitleLineRepository subtitleLineRepository;

    @BeforeEach
    void setupData() {
        cardEnhancementRepository.deleteAll();
        subtitleLineRepository.deleteAll();
        watchedMovieRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        reviewLogRepository.deleteAll();

        // Stub Wyzie Subs
        WireMock.configureFor("localhost", wireMockServer.port());
        stubFor(get(urlPathMatching("/subtitles/tt001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                                1
                                00:01:00,000 --> 00:01:02,500
                                I dream of electric sheep.
                                
                                2
                                00:02:00,000 --> 00:02:02,500
                                Do androids dream?
                                """)));

        // Stub Wiktionary
        stubFor(get(urlPathMatching("/api/rest_v1/page/definition/dream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"en\":[{\"etymology\":\"From Old English dr\\u0113am.\"}]}")));

        // Stub DeepSeek scene summary (non-streaming JSON response)
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing("场景摘要")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[{\"message\":{\"content\":\"科幻场景中关于梦境和意识的对话。\"}}]}")));

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
        tagRepository.save(deck);

        Card card = new Card(DEFAULT_USER_ID, "dream", "梦");
        card.getTags().add(deck);
        cardRepository.save(card);
    }

    @Test
    void fullEnhanceFlow_showsMovieQuoteAndEtymology() {
        // Navigate to review page
        page.navigate("http://localhost:" + serverPort + "/review");
        page.waitForSelector("[data-testid=\"deck-item\"]");

        // Select deck and start review
        page.locator("[data-testid=\"deck-item\"]").first().click();
        page.waitForSelector("[data-testid=\"flip-card-btn\"]");

        // Flip card
        page.locator("[data-testid=\"flip-card-btn\"]").click();
        page.waitForSelector("[data-testid=\"card-back\"]");

        // Click Card Enhance
        page.locator("[data-testid=\"card-enhance-btn\"]").click();

        // Wait for loading to finish and movie quote zone to appear
        page.waitForSelector("[data-testid=\"movie-quote-zone\"]");

        // Verify movie quote zone content
        assertThat(page.locator("[data-testid=\"movie-quote-zone\"]").innerText()).contains("dream");

        // Verify etymology zone
        assertThat(page.locator("[data-testid=\"etymology-zone\"]").innerText()).contains("Old English");

        // Verify CardEnhancement rows saved
        var enhancements = cardEnhancementRepository.findAll();
        assertThat(enhancements).hasSize(2);
        assertThat(enhancements).extracting(CardEnhancement::getStatus)
                .containsOnly(EnhancementStatus.SUCCESS);
    }
}
