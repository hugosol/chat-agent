package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.service.MovieService;
import com.hugosol.chatagent.service.TmdbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieController.class)
@WithMockUser(username = "test-user")
class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @BeforeEach
    void setUp() {
        reset(movieService);
    }

    @Test
    void listMovies_returnsMovieList() throws Exception {
        WatchedMovie movie = new WatchedMovie("test-user", "tt001", "Inception", 2010, SubtitleStatus.DONE);
        when(movieService.listMovies("test-user")).thenReturn(List.of(movie));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt001"))
                .andExpect(jsonPath("$[0].title").value("Inception"))
                .andExpect(jsonPath("$[0].subtitleStatus").value("DONE"));
    }

    @Test
    void importBatch_returnsOk() throws Exception {
        doNothing().when(movieService).importBatch(any(), eq("test-user"));

        mockMvc.perform(post("/api/movies/import/batch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"title":"Inception","imdbId":"tt1375666","year":"2010"}]\
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void deleteMovie_returnsDeleted() throws Exception {
        mockMvc.perform(delete("/api/movies/tt001")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        verify(movieService).deleteMovie("tt001", "test-user");
    }

    @Test
    void searchMovies_returnsCandidates() throws Exception {
        TmdbClient.MovieCandidate candidate = new TmdbClient.MovieCandidate("tt001", "Test Movie", 2022);
        when(movieService.searchTmdb("Test Movie")).thenReturn(List.of(candidate));

        mockMvc.perform(post("/api/movies/search")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Test Movie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt001"))
                .andExpect(jsonPath("$[0].title").value("Test Movie"));
    }

    @Test
    void searchMovies_returnsBadRequestWhenQueryEmpty() throws Exception {
        mockMvc.perform(post("/api/movies/search")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMovie_returnsMovie() throws Exception {
        WatchedMovie movie = new WatchedMovie("test-user", "tt001", "New Movie", 2023, SubtitleStatus.PENDING);
        when(movieService.addMovie(eq("tt001"), eq("New Movie"), eq(2023), eq("test-user")))
                .thenReturn(movie);

        mockMvc.perform(post("/api/movies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imdbId\":\"tt001\",\"title\":\"New Movie\",\"year\":2023}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imdbId").value("tt001"))
                .andExpect(jsonPath("$.title").value("New Movie"));
    }

    @Test
    void addMovie_returnsBadRequestWhenMissingFields() throws Exception {
        mockMvc.perform(post("/api/movies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imdbId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redownloadSubtitles_returnsTriggered() throws Exception {
        mockMvc.perform(post("/api/movies/tt001/download")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("download_triggered"));

        verify(movieService).redownloadSubtitles("tt001", "test-user");
    }
}
