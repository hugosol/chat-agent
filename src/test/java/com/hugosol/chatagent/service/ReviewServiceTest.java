package com.hugosol.chatagent.service;

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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private ReviewService reviewService;
    private static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(cardRepository, preferencesService, reviewLogRepository);
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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW))
                .thenReturn(5L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);

        var result = reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1");

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

        assertThat(result.card().getFirstReviewDate()).isEqualTo(NOW);
        assertThat(result.stats().reviewedToday()).isEqualTo(1);
        assertThat(result.stats().remaining()).isEqualTo(5L);
        assertThat(result.stats().learnedToday()).isEqualTo(1);
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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(2L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW))
                .thenReturn(4L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(0L);

        var result = reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1");

        assertThat(result.card().getFirstReviewDate()).isEqualTo(originalFirstReview);
        assertThat(result.card().getCardState()).isEqualTo(2);
        assertThat(result.stats().learnedToday()).isEqualTo(0);
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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW))
                .thenReturn(5L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);

        reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1");

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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW))
                .thenReturn(4L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(0L);

        reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1");

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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(1L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW))
                .thenReturn(4L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW))
                .thenReturn(0L);

        var result = reviewService.rateCard("card-1", Rating.AGAIN, NOW, "user-1");

        assertThat(result.card().getLapses()).isEqualTo(1);
    }

    @Test
    void rateCard_cardNotFound_throws404() {
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.rateCard("nonexistent", Rating.GOOD, NOW, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rateCard_wrongUser_throws404() {
        Card card = new Card("other-user", "hello", "你好");
        card.setId("card-1");

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1"))
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

        var result = reviewService.getNextCard("deck-1", "STANDARD", 20, "user-1");

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

        var result = reviewService.getNextCard("deck-1", "STANDARD", 20, "user-1");

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

        var result = reviewService.getNextCard("deck-1", "STANDARD", 5, "user-1");

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

        var result = reviewService.getNextCard("deck-1", "REVIEW_ONLY", 20, "user-1");

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

        var result = reviewService.getNextCard("deck-1", "NEW_ONLY", 20, "user-1");

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

        var result = reviewService.getNextCard("deck-1", "NEW_ONLY", 3, "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getNextCard_cram_returnsAnyCard() {
        Card card = new Card("user-1", "hello", "你好");
        card.setId("card-1");

        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findRandomCardByDeckId("deck-1")).thenReturn(Optional.of(card));

        var result = reviewService.getNextCard("deck-1", "CRAM", 20, "user-1");

        assertThat(result).isPresent();
    }

    @Test
    void getNextCard_noCards_returnsEmpty() {
        when(cardRepository.findFirstDueCardByDeckId(eq("deck-1"), any(Instant.class))).thenReturn(Optional.empty());
        when(preferencesService.get("user-1")).thenReturn(defaultPreferences());
        when(cardRepository.findFirstNewCardByDeckId("deck-1")).thenReturn(Optional.empty());

        var result = reviewService.getNextCard("deck-1", "STANDARD", 20, "user-1");

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
        when(cardRepository.countByTagsIdAndLastReviewGreaterThanEqual("deck-tag-id", NOW)).thenReturn(1L);
        when(cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual("deck-tag-id", 0, NOW)).thenReturn(5L);
        when(cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual("deck-tag-id", NOW)).thenReturn(1L);

        reviewService.rateCard("card-1", Rating.GOOD, NOW, "user-1");

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
    }

    private UserPreferences defaultPreferences() {
        UserPreferences prefs = new UserPreferences("user-1");
        prefs.setNewCardDailyLimit(20);
        prefs.setDayStartHour(6);
        return prefs;
    }
}
