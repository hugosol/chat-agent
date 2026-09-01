package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.CardEnhancementRepository;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
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
    private LlmReqConstructor llmReqConstructor;
    @Mock
    private WiktionaryClient wiktionaryClient;

    private CardEnhanceService service;

    @BeforeEach
    void setUp() {
        service = new CardEnhanceService(cardRepository, cardEnhancementRepository,
                subtitleLineRepository, watchedMovieRepository, llmReqConstructor, wiktionaryClient);
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

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("梦境中对话的场景。");
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
        verify(llmReqConstructor, never()).chat(anyList(), any(), anyString(), any());
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

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("Scene summary.");
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

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("Scene summary.");
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

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("Matrix scene.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subtitleLineRepository.countByImdbId("tt001")).thenReturn(1);

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result.movieQuote()).isNotNull();
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
        when(subtitleLineRepository.countByImdbId("tt001")).thenReturn(1);

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result.movieQuote()).isNull();
        assertThat(result.notFoundReason()).isEqualTo("no_other_candidates");
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

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("Scene.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", null, null);

        assertThat(result.movieQuote()).isNotNull();
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

        assertThat(result.movieQuote()).isNull();
        assertThat(result.notFoundReason()).isEqualTo("no_movies");
    }

    @Test
    void requote_excludeRefersToDeletedMovie_doesFreshSearch() {
        // Card was enhanced with a quote from tt001, but tt001 subtitle lines
        // have been deleted (movie removed). Another movie tt002 still has
        // matching subtitles. The guard should detect stale exclusion and
        // perform a fresh full search, returning the tt002 match.
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt002", "The Matrix", 1999, SubtitleStatus.DONE)));

        // tt001 subtitles are gone
        when(subtitleLineRepository.countByImdbId("tt001")).thenReturn(0);

        SubtitleLine m2 = new SubtitleLine("tt002", "The Matrix", "00:10:00,000",
                "00:10:03,000", "I dream of electric sheep.",
                " i dream of electric sheep ", 10);
        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(m2));
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(eq("tt002"), anyInt(), anyInt()))
                .thenReturn(List.of(m2));

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("Matrix scene.");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result.movieQuote()).isNotNull();
        assertThat(result.movieQuote().imdbId()).isEqualTo("tt002");
        assertThat(result.movieQuote().quote()).isEqualTo("I dream of electric sheep.");
        assertThat(result.sceneSummary()).isEqualTo("Matrix scene.");
        verify(subtitleLineRepository).countByImdbId("tt001");
    }

    @Test
    void requote_excludeRefersToDeletedMovie_noOtherMatch_returnsNull() {
        // Card was enhanced with a quote from tt001, but tt001 subtitle lines
        // are gone AND no other movies have matching subtitles. The guard
        // should clear the exclusion, but the full search finds nothing.
        Card card = new Card("user-1", "uniqueWord", "独特词");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt002", "The Matrix", 1999, SubtitleStatus.DONE)));

        // tt001 subtitles are gone
        when(subtitleLineRepository.countByImdbId("tt001")).thenReturn(0);

        // tt002 doesn't have this word either
        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% uniqueword %")))
                .thenReturn(List.of());

        CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

        assertThat(result.movieQuote()).isNull();
        assertThat(result.notFoundReason()).isEqualTo("no_subtitle_match");
        verify(subtitleLineRepository).countByImdbId("tt001");
        verify(cardEnhancementRepository, never()).save(any());
    }

    @Test
    void requote_singleMovieMultipleOccurrences_returnsDifferentOccurrence() {
        // Single movie with 3 occurrences of "dream". Exclude the first one —
        // requote should return one of the remaining occurrences (same movie,
        // different position).
        Card card = new Card("user-1", "dream", "梦");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        when(watchedMovieRepository.findByUserId("user-1")).thenReturn(List.of(
                new WatchedMovie("user-1", "tt001", "Inception", 2010, SubtitleStatus.DONE)));

        when(subtitleLineRepository.countByImdbId("tt001")).thenReturn(3);

        SubtitleLine m1 = new SubtitleLine("tt001", "Inception", "00:05:00,000",
                "00:05:03,000", "You mustn't be afraid to dream.",
                " you mustnt be afraid to dream ", 5);
        SubtitleLine m2 = new SubtitleLine("tt001", "Inception", "00:10:00,000",
                "00:10:03,000", "A dream within a dream.",
                " a dream within a dream ", 10);
        SubtitleLine m3 = new SubtitleLine("tt001", "Inception", "00:20:00,000",
                "00:20:03,000", "Was it all a dream?",
                " was it all a dream ", 20);

        when(subtitleLineRepository.findByImdbIdInAndWordsLowerLike(anyList(), eq("% dream %")))
                .thenReturn(List.of(m1, m2, m3));
        // Context query: lineIndex ± 2 (any of the three could be selected)
        when(subtitleLineRepository.findByImdbIdAndLineIndexBetween(eq("tt001"), anyInt(), anyInt()))
                .thenReturn(List.of(m2));

        when(llmReqConstructor.chat(anyList(), any(), anyString(), any())).thenReturn("梦境场景。");
        when(cardEnhancementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Run multiple times to verify we never get the excluded occurrence
        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            CardEnhanceService.EnhanceResult result = service.requote("card-1", "user-1", "tt001", "00:05:00,000");

            assertThat(result.movieQuote()).isNotNull();
            assertThat(result.movieQuote().imdbId()).isEqualTo("tt001");
            assertThat(result.movieQuote().timestamp()).isNotEqualTo("00:05:00,000");
            if ("00:20:00,000".equals(result.movieQuote().timestamp())) {
                foundDifferent = true;
            }
        }
        assertThat(foundDifferent).as("Should sometimes pick a different occurrence").isTrue();
        verify(cardEnhancementRepository, atLeastOnce()).save(any(CardEnhancement.class));
    }
}
