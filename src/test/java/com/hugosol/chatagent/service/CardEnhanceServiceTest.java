package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.CardEnhancementRepository;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardEnhanceServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private CardEnhancementRepository cardEnhancementRepository;
    @Mock
    private SubtitleLineRepository subtitleLineRepository;
    @Mock
    private WatchedMovieRepository watchedMovieRepository;
    @Mock
    private ChatLanguageModel chatLanguageModel;
    @Mock
    private WiktionaryClient wiktionaryClient;

    private CardEnhanceService service;

    @BeforeEach
    void setUp() {
        service = new CardEnhanceService(cardRepository, cardEnhancementRepository,
                subtitleLineRepository, watchedMovieRepository, chatLanguageModel, wiktionaryClient);
    }

    @Test
    void fullSuccess_returnsMovieQuoteAndEtymology() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));

        SubtitleLine match = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream a little bigger.",
                " you mustnt be afraid to dream a little bigger ", 42);
        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(match));
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(eq("tt001"), eq(40), eq(44)))
                .thenReturn(List.of(match));

        when(chatLanguageModel.chat(anyString())).thenReturn("梦境中对话的场景。");
        when(wiktionaryClient.fetchEtymology("dream")).thenReturn("From Old English drēam.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Re-read includes both newly saved enhancements
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");

        assertThat(result.movieQuote()).isNotNull();
        assertThat(result.movieQuote().movieTitle()).isEqualTo("Inception");
        assertThat(result.sceneSummary()).isEqualTo("梦境中对话的场景。");
        assertThat(result.etymology()).isEqualTo("From Old English drēam.");
        verify(cardEnhancementRepository, atLeastOnce()).save(any(CardEnhancement.class));
    }

    @Test
    void idempotent_returnsCachedWhenBothSuccess() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        CardEnhancement subtitle = new CardEnhancement("card-1", EnhancementType.SUBTITLE,
                EnhancementStatus.SUCCESS,
                "{\"movieTitle\":\"Inception\",\"imdbId\":\"tt001\",\"quote\":\"dream\",\"timestamp\":\"00:05:00\",\"sceneSummary\":\"summary\"}",
                null, null);
        CardEnhancement etymology = new CardEnhancement("card-1", EnhancementType.ETYMOLOGY,
                EnhancementStatus.SUCCESS, "From Old English...", null, null);
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of(subtitle, etymology));

        CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");

        assertThat(result.movieQuote()).isNotNull();
        assertThat(result.etymology()).isEqualTo("From Old English...");
        verify(subtitleLineRepository, never()).findByImdbIdInAndWordsLowerLike(any(), any());
        verify(chatLanguageModel, never()).chat(anyString());
        verify(cardEnhancementRepository, never()).save(any());
    }

    @Test
    void subtitleNotFound_savesNotFoundEnhancement() {
        Card card = new Card("user-1", "xyzzy", "未知");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());
        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));
        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% xyzzy %")))
                .thenReturn(List.of());
        when(wiktionaryClient.fetchEtymology("xyzzy")).thenReturn(null);
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");

        assertThat(result.movieQuote()).isNull();
        assertThat(result.sceneSummary()).isNull();
    }

    @Test
    void cardNotFound_throwsException() {
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enhance("nonexistent", "user-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void noWatchedMovies_skipsSubtitleSearch() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());
        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of());
        when(wiktionaryClient.fetchEtymology("dream")).thenReturn("From Old English.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");

        assertThat(result.movieQuote()).isNull();
        verify(subtitleLineRepository, never()).findByImdbIdInAndWordsLowerLike(any(), any());
    }

    @Test
    void multiMovie_picksFromMultipleMovies() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt002", "The Matrix", 1999, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt003", "Shutter Island", 2010, SubtitleStatus.DONE)));

        SubtitleLine m1 = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 42);
        SubtitleLine m2 = new SubtitleLine("tt002", "The Matrix", "00:10:00,000",
                "00:10:03,000", "I dream of electric sheep.",
                " i dream of electric sheep ", 10);
        SubtitleLine m3 = new SubtitleLine("tt003", "Shutter Island", "00:15:00,000",
                "00:15:03,000", "Was it all a dream?",
                " was it all a dream ", 5);

        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(m1, m2, m3));
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(m1));

        when(chatLanguageModel.chat(anyString())).thenReturn("Scene summary.");
        when(wiktionaryClient.fetchEtymology("dream")).thenReturn(null);
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        Set<String> possibleTitles = Set.of("Inception", "The Matrix", "Shutter Island");

        boolean foundMultiple = false;
        String lastTitle = null;
        for (int i = 0; i < 20; i++) {
            CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");
            assertThat(result.movieQuote()).isNotNull();
            assertThat(result.movieQuote().movieTitle()).isIn(possibleTitles);
            if (lastTitle != null && !lastTitle.equals(result.movieQuote().movieTitle())) {
                foundMultiple = true;
            }
            lastTitle = result.movieQuote().movieTitle();
        }
        assertThat(foundMultiple).as("Should pick from different imdbIds over multiple runs").isTrue();
    }

    @Test
    void singleMovie_picksThatMovie() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));

        SubtitleLine match = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 42);
        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(match));
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(match));

        when(chatLanguageModel.chat(anyString())).thenReturn("Scene summary.");
        when(wiktionaryClient.fetchEtymology("dream")).thenReturn(null);
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardEnhancementRepository.findByCardId("card-1")).thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.enhance("card-1", "user-1");

        assertThat(result.movieQuote()).isNotNull();
        assertThat(result.movieQuote().movieTitle()).isEqualTo("Inception");
    }

    // ── Requote tests ──

    @Test
    void requote_returnsNewQuoteExcludingCurrent() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE),
                new WatchedMovie("user-1", "tt002", "The Matrix", 1999, SubtitleStatus.DONE)));

        SubtitleLine m1 = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 42);
        SubtitleLine m2 = new SubtitleLine("tt002", "The Matrix", "00:10:00,000",
                "00:10:03,000", "I dream of electric sheep.",
                " i dream of electric sheep ", 10);

        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(m1, m2));
        // m1 is excluded, so context fetched for m2
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(eq("tt002"), anyInt(), anyInt()))
                .thenReturn(List.of(m2));

        when(chatLanguageModel.chat(anyString())).thenReturn("Matrix scene.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result).isNotNull();
        assertThat(result.movieQuote().imdbId()).isEqualTo("tt002");
        assertThat(result.movieQuote().quote()).isEqualTo("I dream of electric sheep.");
        assertThat(result.sceneSummary()).isEqualTo("Matrix scene.");
        verify(cardEnhancementRepository, atLeastOnce()).save(any(CardEnhancement.class));
    }

    @Test
    void requote_noOtherMatch_returnsNull() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));

        SubtitleLine only = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 42);

        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(only));

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result).isNull();
        verify(cardEnhancementRepository, never()).save(any());
    }

    @Test
    void requote_nullExclude_doesFullSearch() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));

        SubtitleLine match = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 42);

        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(match));
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(match));

        when(chatLanguageModel.chat(anyString())).thenReturn("Scene.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", null, null);

        assertThat(result).isNotNull();
        assertThat(result.movieQuote().movieTitle()).isEqualTo("Inception");
    }

    @Test
    void requote_cardNotFound_throwsException() {
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requote("nonexistent", "user-1", "tt001", "00:05:00"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void requote_noWatchedMovies_returnsNull() {
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", null, null);

        assertThat(result).isNull();
    }
}
