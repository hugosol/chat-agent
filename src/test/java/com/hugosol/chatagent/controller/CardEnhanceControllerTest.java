package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.service.CardEnhanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(CardEnhanceController.class)
@WithMockUser(username = "test-user")
class CardEnhanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardEnhanceService cardEnhanceService;

    @BeforeEach
    void setUp() {
        reset(cardEnhanceService);
    }

    @Test
    void enhance_returnsFullResult() throws Exception {
        CardEnhanceService.MovieQuote quote = new CardEnhanceService.MovieQuote(
                "Inception", "tt1375666", "You mustn't be afraid to dream a little bigger.", "00:05:00,000");
        CardEnhanceService.EnhanceResult result = new CardEnhanceService.EnhanceResult(
                quote, "梦境中对话的场景。", "From Old English drēam.");

        when(cardEnhanceService.enhance(eq("card-1"), eq("test-user"))).thenReturn(result);

        mockMvc.perform(post("/api/cards/card-1/enhance").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieQuote.movieTitle").value("Inception"))
                .andExpect(jsonPath("$.movieQuote.quote").value("You mustn't be afraid to dream a little bigger."))
                .andExpect(jsonPath("$.sceneSummary").value("梦境中对话的场景。"))
                .andExpect(jsonPath("$.etymology").value("From Old English drēam."));
    }

    @Test
    void enhance_returnsPartialWhenNoSubtitleMatch() throws Exception {
        CardEnhanceService.EnhanceResult result = new CardEnhanceService.EnhanceResult(
                null, null, "From Old English.");

        when(cardEnhanceService.enhance(eq("card-1"), eq("test-user"))).thenReturn(result);

        mockMvc.perform(post("/api/cards/card-1/enhance").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieQuote").doesNotExist())
                .andExpect(jsonPath("$.sceneSummary").doesNotExist())
                .andExpect(jsonPath("$.etymology").value("From Old English."));
    }

    @Test
    void enhance_returnsNotFoundForUnknownCard() throws Exception {
        when(cardEnhanceService.enhance(eq("nonexistent"), eq("test-user")))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Card not found"));

        mockMvc.perform(post("/api/cards/nonexistent/enhance").with(csrf()))
                .andExpect(status().isNotFound());
    }
}
