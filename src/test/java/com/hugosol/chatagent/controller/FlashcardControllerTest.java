package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.service.FlashcardService;

import org.junit.jupiter.api.BeforeEach;
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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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

    @BeforeEach
    void setUp() {
        reset(flashcardService);
    }

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
        tag.setId("tag-id-1");
        card.setTags(Set.of(tag));

        when(flashcardService.createCard(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(card);

        String requestJson = """
                {"front":"yesterday","back":"昨天","tagIds":["tag-1"]}""";

        mockMvc.perform(post("/api/cards/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("card-1"))
                .andExpect(jsonPath("$.front").value("yesterday"))
                .andExpect(jsonPath("$.back").value("昨天"))
                .andExpect(jsonPath("$.tags[0].id").isNotEmpty())
                .andExpect(jsonPath("$.tags[0].name").value("daily"))
                .andExpect(jsonPath("$.tags[0].type").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin")
    void getTags_returnsUserTags() throws Exception {
        when(flashcardService.getTags("admin"))
                .thenReturn(List.of(
                        tagWithId("daily", "admin", "t1"),
                        tagWithId("time", "admin", "t2")
                ));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[0].name").value("daily"))
                .andExpect(jsonPath("$[0].type").isEmpty())
                .andExpect(jsonPath("$[1].id").value("t2"))
                .andExpect(jsonPath("$[1].name").value("time"));
    }

    @Test
    @WithMockUser(username = "admin")
    void getTags_withTypeDeck_returnsFilteredTags() throws Exception {
        when(flashcardService.getTagsByType("admin", "deck"))
                .thenReturn(List.of(tagWithId("daily", "admin", "t1")));

        mockMvc.perform(get("/api/tags").queryParam("type", "deck"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[0].name").value("daily"));
    }

    @Test
    @WithMockUser(username = "admin")
    void addCard_emptyTags_returnsBadRequest() throws Exception {
        when(flashcardService.createCard(anyString(), anyString(), anyList(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不能为空"));

        String requestJson = """
                {"front":"yesterday","back":"昨天","tagIds":[]}""";

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
                {"front":"yesterday","back":"昨天","tagIds":["tag-1"]}""";

        mockMvc.perform(post("/api/cards/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void createTag_returnsCreatedTag() throws Exception {
        Tag tag = tagWithId("daily", "admin", "t1");
        tag.setType("deck");

        when(flashcardService.createTag(any(), any(), any()))
                .thenReturn(tag);

        String requestJson = """
                {"name":"daily","type":"deck"}""";

        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.name").value("daily"))
                .andExpect(jsonPath("$.type").value("deck"));
    }

    @Test
    @WithMockUser(username = "admin")
    void createTag_emptyName_returns422() throws Exception {
        when(flashcardService.createTag(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签名不能为空"));

        String requestJson = """
                {"name":"","type":null}""";

        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void createTag_duplicateName_returns422() throws Exception {
        when(flashcardService.createTag(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签'daily'已存在"));

        String requestJson = """
                {"name":"daily","type":"deck"}""";

        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void updateTag_returnsUpdatedTag() throws Exception {
        Tag tag = tagWithId("daily-updated", "admin", "t1");
        tag.setType("deck");

        when(flashcardService.updateTag(any(), any(), any(), any()))
                .thenReturn(tag);

        String requestJson = """
                {"name":"daily-updated","type":"deck"}""";

        mockMvc.perform(MockMvcRequestBuilders.put("/api/tags/t1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.name").value("daily-updated"));
    }

    @Test
    @WithMockUser(username = "admin")
    void updateTag_notFound_returns404() throws Exception {
        when(flashcardService.updateTag(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        String requestJson = """
                {"name":"x","type":null}""";

        mockMvc.perform(MockMvcRequestBuilders.put("/api/tags/nonexistent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin")
    void updateTag_duplicateName_returns422() throws Exception {
        when(flashcardService.updateTag(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签'duplicate'已存在"));

        String requestJson = """
                {"name":"duplicate","type":null}""";

        mockMvc.perform(MockMvcRequestBuilders.put("/api/tags/t1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteTag_returnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/tags/t1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteTag_notFound_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(flashcardService).deleteTag(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/tags/nonexistent")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteTag_orphanCards_returns422() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "{\"orphanCount\":3}"))
                .when(flashcardService).deleteTag(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/tags/t1")
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin")
    void listCards_returnsPagedCards() throws Exception {
        Card card = new Card("admin", "yesterday", "昨天");
        card.setId("card-1");
        card.setCardState(0);
        card.setDue(Instant.parse("2026-05-30T10:00:00Z"));
        Tag tag = tagWithId("daily", "admin", "t1");
        tag.setType("deck");
        card.setTags(Set.of(tag));

        when(flashcardService.listCards(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(card), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("card-1"))
                .andExpect(jsonPath("$.content[0].front").value("yesterday"))
                .andExpect(jsonPath("$.content[0].tags[0].id").value("t1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin")
    void updateCard_returnsUpdatedCard() throws Exception {
        Card card = new Card("admin", "yesterday-updated", "昨天修改");
        card.setId("card-1");
        card.setCardState(0);
        card.setDue(Instant.parse("2026-05-30T10:00:00Z"));

        when(flashcardService.updateCard(any(), any(), any(), any(), any()))
                .thenReturn(card);

        String requestJson = """
                {"front":"yesterday-updated","back":"昨天修改","tagIds":["tag-1"]}""";

        mockMvc.perform(MockMvcRequestBuilders.put("/api/cards/card-1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.front").value("yesterday-updated"));
    }

    @Test
    @WithMockUser(username = "admin")
    void updateCard_notFound_returns404() throws Exception {
        when(flashcardService.updateCard(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        String requestJson = """
                {"front":"x","back":"y","tagIds":[]}""";

        mockMvc.perform(MockMvcRequestBuilders.put("/api/cards/nonexistent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteCard_returnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/cards/card-1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteCard_notFound_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(flashcardService).deleteCard(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/cards/nonexistent")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    private static Tag tagWithId(String name, String userId, String id) {
        Tag tag = new Tag(name, userId);
        tag.setId(id);
        return tag;
    }
}
