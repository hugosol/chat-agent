package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.ForgetDeckResult;
import com.hugosol.chatagent.service.ReviewService;
import com.hugosol.chatagent.service.UserPreferencesService;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
