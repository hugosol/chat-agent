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
                "you mustnt be afraid to dream a little bigger", 42);
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
}
