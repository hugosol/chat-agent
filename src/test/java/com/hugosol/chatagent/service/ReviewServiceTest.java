package com.hugosol.chatagent.service;

import com.hugosol.chatagent.dto.ForgetDeckResult;
import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserPreferencesService preferencesService;

    @Mock
    private ReviewLogRepository reviewLogRepository;

    @Mock
    private FsrsConfigService fsrsConfigService;

    private ReviewService reviewService;
    private static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(fsrsConfigService.getConfig(anyString())).thenReturn(FsrsSchedulerConfig.defaults());
        reviewService = new ReviewService(cardRepository, preferencesService, reviewLogRepository, fsrsConfigService);
        lenient().when(cardRepository.countByTagsIdAndCardState(anyString(), eq(0))).thenReturn(1000L);
    }

    @Test
    void rateCard_newCard_setsFirstReviewDateAndUpdatesFsrsFields() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setDue(NOW);
        card.setReps(0);
        card.setLapses(0);
        card.setLastReview(null);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-tag-id");

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card saved = captor.getValue();

        assertThat(saved.getFirstReviewDate()).isEqualTo(NOW);
        assertThat(saved.getCardState()).isNotEqualTo(0);
        assertThat(saved.getStability()).isGreaterThan(1.0);
        assertThat(saved.getDifficulty()).isGreaterThan(0.0);
        assertThat(saved.getReps()).isEqualTo(1);
        assertThat(saved.getLapses()).isEqualTo(0);
        assertThat(saved.getLastReview()).isEqualTo(NOW);

        assertThat(result.getFirstReviewDate()).isEqualTo(NOW);
    }

    @Test
    void rateCard_reviewedCard_doesNotOverwriteFirstReviewDate() {
        Instant originalFirstReview = Instant.parse("2026-06-01T10:00:00Z");
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(3.0);
        card.setCardState(2);
        card.setDue(Instant.parse("2026-06-03T10:00:00Z"));
        card.setReps(2);
        card.setLapses(0);
        card.setLastReview(Instant.parse("2026-06-01T10:00:00Z"));
        card.setFirstReviewDate(originalFirstReview);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-tag-id");

        assertThat(result.getFirstReviewDate()).isEqualTo(originalFirstReview);
        assertThat(result.getCardState()).isEqualTo(2);
    }

    @Test
    void rateCard_newCard_goodPersistsLearningStep() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setDue(NOW);
        card.setReps(0);
        card.setLapses(0);
        card.setLastReview(null);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-tag-id");

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card saved = captor.getValue();

        assertThat(saved.getStep()).isEqualTo(1);
    }

    @Test
    void rateCard_learningCard_stepGraduatesOnLastStep() {
        Instant pastReview = Instant.parse("2026-06-04T10:00:00Z");
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.3065);
        card.setDifficulty(2.1181);
        card.setCardState(1);
        card.setStep(1);
        card.setDue(NOW.plusSeconds(600));
        card.setReps(1);
        card.setLapses(0);
        card.setLastReview(pastReview);
        card.setFirstReviewDate(pastReview);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-tag-id");

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card saved = captor.getValue();

        assertThat(saved.getCardState()).isEqualTo(2);
        assertThat(saved.getStep()).isEqualTo(-1);
    }

    @Test
    void rateCard_againRating_incrementsLapses() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(3.0);
        card.setCardState(2);
        card.setDue(Instant.parse("2026-06-03T10:00:00Z"));
        card.setReps(3);
        card.setLapses(0);
        card.setLastReview(Instant.parse("2026-06-01T10:00:00Z"));

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = reviewService.rateCard("card-1", Rating.AGAIN, "STANDARD", NOW, "user-1", "deck-tag-id");

        assertThat(result.getLapses()).isEqualTo(1);
    }

    @Test
    void rateCard_cardNotFound_throws404() {
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.rateCard("nonexistent", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rateCard_wrongUser_throws404() {
        Card card = new Card("other-user", "hello", "你好");
        card.setId("card-1");

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getNextCard_standard_returnsDueCard() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setCardState(2);
        card.setDue(NOW.minusSeconds(3600));

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.of(card));

        var result = reviewService.getNextCard("deck-1", "STANDARD", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("card-1");
    }

    @Test
    void getNextCard_standard_fallsBackToNewCard() {
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.empty());

        Card newCard = new Card("user-1", "new", "新");
        newCard.setId("card-2");
        newCard.setCardState(0);

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstNewCardByDeckId("deck-1")).thenReturn(Optional.of(newCard));

        var result = reviewService.getNextCard("deck-1", "STANDARD", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("card-2");
    }

    @Test
    void getNextCard_standard_limitReached_skipsNewCard() {
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.empty());

        UserPreferences prefs = defaultPreferences();
        prefs.setNewCardDailyLimit(5);
        when(preferencesService.get("user-1")).thenReturn(prefs);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(5L);

        var result = reviewService.getNextCard("deck-1", "STANDARD", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getNextCard_reviewOnly_skipsNewCards() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setCardState(2);
        card.setDue(NOW.minusSeconds(3600));

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.of(card));

        var result = reviewService.getNextCard("deck-1", "REVIEW_ONLY", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getCardState()).isEqualTo(2);
    }

    @Test
    void getNextCard_newOnly_returnsNewCard() {
        Card newCard = new Card("user-1", "new", "新");
        newCard.setId("card-2");
        newCard.setCardState(0);

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstNewCardByDeckId("deck-1")).thenReturn(Optional.of(newCard));

        var result = reviewService.getNextCard("deck-1", "NEW_ONLY", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getCardState()).isEqualTo(0);
    }

    @Test
    void getNextCard_newOnly_limitReached_returnsEmpty() {
        UserPreferences prefs = defaultPreferences();
        prefs.setNewCardDailyLimit(3);
        when(preferencesService.get("user-1")).thenReturn(prefs);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(3L);

        var result = reviewService.getNextCard("deck-1", "NEW_ONLY", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getNextCard_cram_returnsAnyCard() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findRandomCardByDeckId("deck-1")).thenReturn(Optional.of(card));

        var result = reviewService.getNextCard("deck-1", "CRAM", "user-1");

        assertThat(result).isPresent();
    }

    @Test
    void getNextCard_noCards_returnsEmpty() {
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.empty());
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstNewCardByDeckId("deck-1")).thenReturn(Optional.empty());

        var result = reviewService.getNextCard("deck-1", "STANDARD", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void rateCard_createsReviewLog() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(2.5);
        card.setDifficulty(0.0);
        card.setCardState(0);
        card.setStep(-1);
        card.setDue(NOW);
        card.setReps(0);
        card.setLapses(0);
        card.setLastReview(null);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");
        card.setTags(Set.of(deckTag));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = reviewService.rateCard("card-1", Rating.GOOD, "STANDARD", NOW, "user-1", "deck-tag-id");

        ArgumentCaptor<ReviewLog> captor = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(captor.capture());
        ReviewLog log = captor.getValue();

        assertThat(log.getUserId()).isEqualTo("user-1");
        assertThat(log.getCardId()).isEqualTo("card-1");
        assertThat(log.getRating()).isEqualTo(Rating.GOOD);
        assertThat(log.getStateBefore()).isEqualTo(1);
        assertThat(log.isFirstReview()).isTrue();
        assertThat(log.getDeckId()).isEqualTo("deck-tag-id");
        assertThat(log.getReviewedAt()).isEqualTo(NOW);

        assertThat(result.getFirstReviewDate()).isEqualTo(NOW);
        assertThat(result.getCardState()).isNotEqualTo(0);
        assertThat(result.getStability()).isGreaterThan(1.0);
    }

    @Test
    void computeReviewStats_standard_returnsDueCountPlusNewQuota() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(2L);
        when(cardRepository.countDueCardsByTagsId(eq("deck-1"), any(Instant.class)))
                .thenReturn(3L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(5L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "STANDARD", "user-1");

        assertThat(stats.reviewedToday()).isEqualTo(2);
        assertThat(stats.remaining()).isEqualTo(18L);
        assertThat(stats.learnedToday()).isEqualTo(5);
        assertThat(stats.dailyLimit()).isEqualTo(20);
    }

    @Test
    void computeReviewStats_reviewOnly_returnsDueCountOnly() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(1L);
        when(cardRepository.countDueCardsByTagsId(eq("deck-1"), any(Instant.class)))
                .thenReturn(4L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(0L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "REVIEW_ONLY", "user-1");

        assertThat(stats.remaining()).isEqualTo(4L);
        assertThat(stats.learnedToday()).isEqualTo(0);
    }

    @Test
    void computeReviewStats_newOnly_returnsNewQuotaOnly() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(3L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(8L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "NEW_ONLY", "user-1");

        assertThat(stats.remaining()).isEqualTo(12L);
        assertThat(stats.learnedToday()).isEqualTo(8);
    }

    @Test
    void computeReviewStats_cram_returnsNegativeSentinel() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(10L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(5L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "CRAM", "user-1");

        assertThat(stats.remaining()).isEqualTo(-1);
        assertThat(stats.learnedToday()).isEqualTo(-1);
    }

    @Test
    void computeReviewStats_standard_capsNewCardRemainingByActualCount() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(2L);
        when(cardRepository.countDueCardsByTagsId(eq("deck-1"), any(Instant.class)))
                .thenReturn(5L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(0L);
        when(cardRepository.countByTagsIdAndCardState("deck-1", 0)).thenReturn(3L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "STANDARD", "user-1");

        assertThat(stats.remaining()).isEqualTo(8L);
    }

    @Test
    void computeReviewStats_newOnly_capsNewCardRemainingByActualCount() {
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(3L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(eq("deck-1"), any(Instant.class)))
                .thenReturn(0L);
        when(cardRepository.countByTagsIdAndCardState("deck-1", 0)).thenReturn(2L);
        when(cardRepository.findFirstDueByTagsIdAndDueAfter(eq("deck-1"), any(Instant.class)))
                .thenReturn(null);

        var stats = reviewService.computeReviewStats("deck-1", "NEW_ONLY", "user-1");

        assertThat(stats.remaining()).isEqualTo(2L);
    }

    @Test
    void forgetCard_resetsFsrsStateToNew() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(15.0);
        card.setDifficulty(5.0);
        card.setCardState(2);
        card.setDue(Instant.parse("2026-07-01T10:00:00Z"));
        card.setReps(10);
        card.setLapses(3);
        card.setStep(2);
        card.setLastReview(Instant.parse("2026-06-01T10:00:00Z"));
        card.setFirstReviewDate(Instant.parse("2026-05-01T10:00:00Z"));

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        reviewService.forgetCard("card-1", "user-1");

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card saved = captor.getValue();

        assertThat(saved.getStability()).isEqualTo(2.5);
        assertThat(saved.getDifficulty()).isEqualTo(0.0);
        assertThat(saved.getCardState()).isEqualTo(0);
        assertThat(saved.getStep()).isEqualTo(-1);
        assertThat(saved.getReps()).isEqualTo(0);
        assertThat(saved.getLapses()).isEqualTo(0);
        assertThat(saved.getLastReview()).isNull();
        assertThat(saved.getFirstReviewDate()).isNull();
    }

    @Test
    void forgetCard_deletesAllReviewLogs() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");
        card.setStability(15.0);
        card.setDifficulty(5.0);
        card.setCardState(2);
        card.setDue(Instant.parse("2026-07-01T10:00:00Z"));
        card.setReps(10);
        card.setLapses(3);

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        reviewService.forgetCard("card-1", "user-1");

        verify(reviewLogRepository).deleteByCardId("card-1");
    }

    @Test
    void forgetCard_cardNotFound_throws404() {
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.forgetCard("nonexistent", "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forgetCard_wrongUser_throws404() {
        Card card = new Card("other-user", "hello", "你好");
        card.setId("card-1");

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> reviewService.forgetCard("card-1", "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forgetDeck_resetsAllCardsAndDeletesLogs() {
        Card card1 = new Card("user-1", "hello", "你好");
        card1.setId("card-1");
        card1.setStability(10.0);
        card1.setDifficulty(3.0);
        card1.setCardState(2);
        card1.setDue(Instant.parse("2026-07-01T10:00:00Z"));
        card1.setReps(5);
        card1.setLapses(1);

        Card card2 = new Card("user-1", "world", "世界");
        card2.setId("card-2");
        card2.setStability(20.0);
        card2.setDifficulty(4.0);
        card2.setCardState(2);
        card2.setDue(Instant.parse("2026-08-01T10:00:00Z"));
        card2.setReps(8);
        card2.setLapses(2);

        when(cardRepository.findByFilteredDeckIds("deck-1", "user-1"))
                .thenReturn(List.of("card-1", "card-2"));
        when(reviewLogRepository.countByCardId("card-1")).thenReturn(3);
        when(reviewLogRepository.countByCardId("card-2")).thenReturn(4);
        when(cardRepository.findAllById(List.of("card-1", "card-2")))
                .thenReturn(List.of(card1, card2));
        when(cardRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ForgetDeckResult result = reviewService.forgetDeck("deck-1", "user-1");

        assertThat(result.cardCount()).isEqualTo(2);
        assertThat(result.deletedReviewCount()).isEqualTo(7);
        verify(reviewLogRepository).deleteByCardIdIn(List.of("card-1", "card-2"));
        verify(cardRepository).saveAll(anyList());
    }

    @Test
    void forgetDeck_emptyDeck_returnsZeroCounts() {
        when(cardRepository.findByFilteredDeckIds("deck-1", "user-1"))
                .thenReturn(List.of());

        ForgetDeckResult result = reviewService.forgetDeck("deck-1", "user-1");

        assertThat(result.cardCount()).isEqualTo(0);
        assertThat(result.deletedReviewCount()).isEqualTo(0);
        verify(reviewLogRepository, never()).deleteByCardIdIn(anyList());
    }

    private UserPreferences defaultPreferences() {
        UserPreferences prefs = new UserPreferences("user-1");
        prefs.setNewCardDailyLimit(20);
        prefs.setDayStartHour(6);
        return prefs;
    }
}
