package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.service.FlashcardService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlashcardController.class)
class FlashcardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlashcardService flashcardService;

    @Test
    @WithMockUser(username = "admin")
    void addCard_returnsCardWithTags() throws Exception {
        Card card = new Card("admin", "yesterday", "昨天");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setDue(Instant.parse("2026-05-30T10:00:00Z"));
        card.setReps(0);
        card.setLapses(0);
        Tag tag = new Tag("daily", "admin");
        card.setTags(Set.of(tag));

        when(flashcardService.createCard(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(card);

        String requestJson = """
                {"front":"yesterday","back":"昨天","tags":["daily"]}""";

        mockMvc.perform(post("/api/cards/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("card-1"))
                .andExpect(jsonPath("$.front").value("yesterday"))
                .andExpect(jsonPath("$.back").value("昨天"))
                .andExpect(jsonPath("$.tags[0].name").value("daily"))
                .andExpect(jsonPath("$.tags[0].type").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin")
    void getTags_returnsUserTags() throws Exception {
        when(flashcardService.getTags("admin"))
                .thenReturn(List.of(
                        new Tag("daily", "admin"),
                        new Tag("time", "admin")
                ));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("daily"))
                .andExpect(jsonPath("$[0].type").isEmpty())
                .andExpect(jsonPath("$[1].name").value("time"));
    }

    @Test
    @WithMockUser(username = "admin")
    void addCard_emptyTags_returnsBadRequest() throws Exception {
        when(flashcardService.createCard(anyString(), anyString(), anyList(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不能为空"));

        String requestJson = """
                {"front":"yesterday","back":"昨天","tags":[]}""";

        mockMvc.perform(post("/api/cards/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void addCard_duplicate_returnsConflict() throws Exception {
        when(flashcardService.createCard(anyString(), anyString(), anyList(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "卡片'yesterday'已存在"));

        String requestJson = """
                {"front":"yesterday","back":"昨天","tags":["daily"]}""";

        mockMvc.perform(post("/api/cards/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }
}
