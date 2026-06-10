package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.ForgetDeckResult;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.service.ReviewService;
import com.hugosol.chatagent.service.ReviewStats;
import com.hugosol.chatagent.service.UserPreferencesService;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private UserPreferencesService preferencesService;

    @MockBean
    private TagRepository tagRepository;

    @MockBean
    private CardRepository cardRepository;

    @BeforeEach
    void setUp() {
        reset(reviewService);
    }

    @Test
    @WithMockUser(username = "admin")
    void forgetCard_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/cards/card-1/forget")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("card-1"))
                .andExpect(jsonPath("$.cardState").value(0));

        verify(reviewService).forgetCard(eq("card-1"), eq("admin"));
    }

    @Test
    @WithMockUser(username = "admin")
    void startReview_includesPreviewField() throws Exception {
        Card card = new Card("admin", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setDue(Instant.now());
        card.setReps(0);
        card.setLapses(0);

        Tag deckTag = new Tag("daily", "admin");
        deckTag.setId("deck-1");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        java.util.Map<Rating, CardState> previewMap = new java.util.LinkedHashMap<>();
        Instant now = Instant.now();
        previewMap.put(Rating.AGAIN, new CardState(0.212, 6.4133, 1, 0, now.plusSeconds(60), 1, 0, now, 0.0, true));
        previewMap.put(Rating.HARD, new CardState(1.2931, 5.1122, 1, 0, now.plusSeconds(540), 1, 0, now, 0.0, true));
        previewMap.put(Rating.GOOD, new CardState(2.3065, 2.1181, 1, 1, now.plusSeconds(600), 1, 0, now, 0.0, true));
        previewMap.put(Rating.EASY, new CardState(8.2956, 1.0, 2, -1, now.plusSeconds(86400 * 4), 1, 0, now, 0.0, true));

        var nextCardAndStats = new com.hugosol.chatagent.dto.NextCardAndStats(Optional.of(card),
                new ReviewStats(0, 10, 0, 20, null));
        when(reviewService.getNextCardAndStats(eq("deck-1"), eq("STANDARD"), eq("admin")))
                .thenReturn(nextCardAndStats);
        when(reviewService.previewCard(any(Card.class), any(Instant.class)))
                .thenReturn(previewMap);

        mockMvc.perform(get("/api/review/start")
                        .param("deckId", "deck-1")
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").exists())
                .andExpect(jsonPath("$.preview.AGAIN").exists())
                .andExpect(jsonPath("$.preview.HARD").exists())
                .andExpect(jsonPath("$.preview.GOOD").exists())
                .andExpect(jsonPath("$.preview.EASY").exists())
                .andExpect(jsonPath("$.preview.GOOD.stability").value(2.3065))
                .andExpect(jsonPath("$.preview.EASY.state").value(2));
    }

    @Test
    @WithMockUser(username = "admin")
    void nextReview_includesPreviewField() throws Exception {
        Card nextCard = new Card("admin", "world", "世界");
        nextCard.setId("card-2");
        nextCard.setStability(2.5);
        nextCard.setDifficulty(0.0);
        nextCard.setCardState(0);
        nextCard.setDue(Instant.now());
        nextCard.setReps(0);
        nextCard.setLapses(0);

        Tag deckTag = new Tag("daily", "admin");
        deckTag.setId("deck-1");
        deckTag.setType("deck");
        nextCard.setTags(Set.of(deckTag));

        java.util.Map<Rating, CardState> previewMap = new java.util.LinkedHashMap<>();
        Instant now = Instant.now();
        previewMap.put(Rating.AGAIN, new CardState(0.212, 6.4133, 1, 0, now.plusSeconds(60), 1, 0, now, 0.0, true));
        previewMap.put(Rating.HARD, new CardState(1.2931, 5.1122, 1, 0, now.plusSeconds(540), 1, 0, now, 0.0, true));
        previewMap.put(Rating.GOOD, new CardState(2.3065, 2.1181, 1, 1, now.plusSeconds(600), 1, 0, now, 0.0, true));
        previewMap.put(Rating.EASY, new CardState(8.2956, 1.0, 2, -1, now.plusSeconds(86400 * 4), 1, 0, now, 0.0, true));

        var nextCardAndStats = new com.hugosol.chatagent.dto.NextCardAndStats(Optional.of(nextCard),
                new ReviewStats(1, 9, 1, 20, null));
        when(reviewService.getNextCardAndStats(eq("deck-1"), eq("STANDARD"), eq("admin")))
                .thenReturn(nextCardAndStats);
        when(reviewService.previewCard(any(Card.class), any(Instant.class)))
                .thenReturn(previewMap);

        mockMvc.perform(post("/api/review/next")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":\"card-1\",\"rating\":\"GOOD\",\"deckId\":\"deck-1\",\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").exists())
                .andExpect(jsonPath("$.preview.AGAIN").exists())
                .andExpect(jsonPath("$.preview.HARD").exists())
                .andExpect(jsonPath("$.preview.GOOD").exists())
                .andExpect(jsonPath("$.preview.EASY").exists())
                .andExpect(jsonPath("$.preview.GOOD.stability").value(2.3065));
    }

    @Test
    @WithMockUser(username = "admin")
    void forgetDeck_validRequest_returns200() throws Exception {
        when(reviewService.forgetDeck(eq("deck-1"), eq("admin")))
                .thenReturn(new ForgetDeckResult(5, 12));

        mockMvc.perform(post("/api/cards/forget")
                        .param("deckId", "deck-1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardCount").value(5))
                .andExpect(jsonPath("$.totalDeletedReviewCount").value(12));

        verify(reviewService).forgetDeck(eq("deck-1"), eq("admin"));
    }

    @Test
    @WithMockUser(username = "admin")
    void putPreferences_invalidLearningSteps_returns400() throws Exception {
        mockMvc.perform(put("/api/user/preferences")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learningSteps\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.learningSteps").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    void putPreferences_invalidDesiredRetention_returns400() throws Exception {
        mockMvc.perform(put("/api/user/preferences")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredRetention\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.desiredRetention").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    void getPreferences_includesFsrsFields() throws Exception {
        UserPreferences prefs = new UserPreferences("admin");
        prefs.setLearningSteps("1m,10m");
        prefs.setRelearningSteps("10m");
        prefs.setDesiredRetention(0.9);
        prefs.setMaximumInterval(36500);
        prefs.setEnableFuzz(true);
        prefs.setShuffleDueCards(false);
        prefs.setUtcOffset(8);
        when(preferencesService.get("admin")).thenReturn(prefs);

        mockMvc.perform(get("/api/user/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learningSteps").value("1m,10m"))
                .andExpect(jsonPath("$.relearningSteps").value("10m"))
                .andExpect(jsonPath("$.desiredRetention").value(0.9))
                .andExpect(jsonPath("$.maximumInterval").value(36500))
                .andExpect(jsonPath("$.enableFuzz").value(true))
                .andExpect(jsonPath("$.shuffleDueCards").value(false))
                .andExpect(jsonPath("$.utcOffset").value(8));
    }

    @Test
    @WithMockUser(username = "admin")
    void putPreferences_saveFsrsFields() throws Exception {
        UserPreferences prefs = new UserPreferences("admin");
        when(preferencesService.get("admin")).thenReturn(prefs);

        mockMvc.perform(put("/api/user/preferences")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredRetention\":0.85,\"enableFuzz\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredRetention").value(0.85))
                .andExpect(jsonPath("$.enableFuzz").value(false));

        verify(preferencesService).save(prefs);
    }

    @Test
    @WithMockUser(username = "admin")
    void putPreferences_partialUpdate_keepsOldValues() throws Exception {
        UserPreferences prefs = new UserPreferences("admin");
        prefs.setLearningSteps("1m,10m");
        prefs.setDesiredRetention(0.9);
        when(preferencesService.get("admin")).thenReturn(prefs);

        mockMvc.perform(put("/api/user/preferences")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastDeckId\":\"deck-1\",\"lastMode\":\"STANDARD\",\"newCardDailyLimit\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastDeckId").value("deck-1"))
                .andExpect(jsonPath("$.learningSteps").value("1m,10m"))
                .andExpect(jsonPath("$.desiredRetention").value(0.9));
    }
}
