package com.hugosol.chatagent.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

class FsrsSchedulerTest {

    private static final Instant BASE_TIME = Instant.parse("2022-11-29T12:30:00Z");

    private static final double TOLERANCE = 1e-4;

    @Test
    void initNewCard_createsLearningCardWithoutStability() {
        Instant now = BASE_TIME;

        CardState state = FsrsScheduler.initNewCard(now);

        assertThat(state.state()).isEqualTo(CardState.STATE_LEARNING);
        assertThat(state.step()).isEqualTo(0);
        assertThat(state.hasStability()).isFalse();
        assertThat(state.due()).isEqualTo(now);
        assertThat(state.reps()).isEqualTo(0);
        assertThat(state.lapses()).isEqualTo(0);
        assertThat(state.lastReview()).isNull();
    }

    @Test
    void firstRepeat_perRating_givesCorrectInitialStabilityAndDifficulty() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);

        CardState againResult = FsrsScheduler.repeat(card, Rating.AGAIN, BASE_TIME, null);
        CardState hardResult = FsrsScheduler.repeat(card, Rating.HARD, BASE_TIME, null);
        CardState goodResult = FsrsScheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        CardState easyResult = FsrsScheduler.repeat(card, Rating.EASY, BASE_TIME, null);

        assertThat(againResult.stability()).isCloseTo(0.212, within(TOLERANCE));
        assertThat(hardResult.stability()).isCloseTo(1.2931, within(TOLERANCE));
        assertThat(goodResult.stability()).isCloseTo(2.3065, within(TOLERANCE));
        assertThat(easyResult.stability()).isCloseTo(8.2956, within(TOLERANCE));

        assertThat(againResult.difficulty()).isCloseTo(6.4133, within(TOLERANCE));
        assertThat(hardResult.difficulty()).isCloseTo(5.11217071, within(TOLERANCE));
        assertThat(goodResult.difficulty()).isCloseTo(2.11810397, within(TOLERANCE));
        assertThat(easyResult.difficulty()).isCloseTo(1.0, within(TOLERANCE));

        for (CardState r : new CardState[]{againResult, hardResult, goodResult, easyResult}) {
            assertThat(r.reps()).isEqualTo(1);
            assertThat(r.lapses()).isEqualTo(0);
        }

        assertThat(againResult.state()).isEqualTo(CardState.STATE_LEARNING);
        assertThat(hardResult.state()).isEqualTo(CardState.STATE_LEARNING);
        assertThat(goodResult.state()).isEqualTo(CardState.STATE_LEARNING);
        assertThat(easyResult.state()).isEqualTo(CardState.STATE_REVIEW);
    }

    @Test
    void retrievability_newCard_returnsZero() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        double r = FsrsScheduler.retrievability(card, BASE_TIME);
        assertThat(r).isEqualTo(0.0);
    }

    @Test
    void retrievability_afterReview_returnsReasonableValue() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        card = FsrsScheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), null);

        double r = FsrsScheduler.retrievability(card, card.due());
        assertThat(r).isCloseTo(0.9, within(0.01));
    }

    @Test
    void repeat_13ConsecutiveReviews_producesCorrectIntervals() {
        Rating[] ratings = {
                Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
                Rating.AGAIN, Rating.AGAIN,
                Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD
        };
        int[] expectedIntervals = {0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21};

        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < ratings.length; i++) {
            card = FsrsScheduler.repeat(card, ratings[i], now, null);
            int ivl = (int) Duration.between(card.lastReview(), card.due()).toDays();
            assertThat(ivl).as("interval " + i).isEqualTo(expectedIntervals[i]);
            now = card.due();
        }
    }

    @Test
    void repeat_6Reviews_producesPreciseStabilityAndDifficulty() {
        Rating[] ratings = {Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD};
        int[] advanceDays = {0, 0, 1, 3, 8, 21};

        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < ratings.length; i++) {
            now = now.plus(Duration.ofDays(advanceDays[i]));
            card = FsrsScheduler.repeat(card, ratings[i], now, null);
        }

        assertThat(card.stability()).isCloseTo(53.62691, within(TOLERANCE));
        assertThat(card.difficulty()).isCloseTo(6.3574867, within(TOLERANCE));
    }

    @Test
    void repeat_10Easy_hitsDifficultyFloor() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < 10; i++) {
            now = now.plus(Duration.ofSeconds(1));
            card = FsrsScheduler.repeat(card, Rating.EASY, now, null);
        }

        assertThat(card.difficulty()).isEqualTo(1.0);
    }

    @Test
    void repeat_1000Again_stabilityAtLeastMinimum() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < 1000; i++) {
            now = now.plus(Duration.ofDays(1));
            card = FsrsScheduler.repeat(card, Rating.AGAIN, now, null);
            assertThat(card.stability()).isGreaterThanOrEqualTo(FsrsScheduler.STABILITY_MIN);
        }
    }

    @Test
    void repeat_fuzzDeterministic_seed42GivesConsistentInterval() {
        AleaPrng alea = new AleaPrng(42);
        CardState card = FsrsScheduler.initNewCard(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        card = FsrsScheduler.repeat(card, Rating.GOOD, now, alea::next);
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        Instant prevDue = card.due();
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        int interval = (int) Duration.between(prevDue, card.due()).toDays();

        assertThat(interval).isEqualTo(12);
    }

    @Test
    void repeat_fuzzDeterministic_seed12345GivesConsistentInterval() {
        AleaPrng alea = new AleaPrng(12345);
        CardState card = FsrsScheduler.initNewCard(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        card = FsrsScheduler.repeat(card, Rating.GOOD, now, alea::next);
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        Instant prevDue = card.due();
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        int interval = (int) Duration.between(prevDue, card.due()).toDays();

        assertThat(interval).isEqualTo(11);
    }

    @Test
    void repeat_hardModifier_hardIntervalShorterThanGood() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);

        card = FsrsScheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), null);

        CardState hardCard = FsrsScheduler.initNewCard(BASE_TIME);
        hardCard = FsrsScheduler.repeat(hardCard, Rating.GOOD, BASE_TIME, null);
        hardCard = FsrsScheduler.repeat(hardCard, Rating.GOOD, hardCard.due(), null);

        Instant goodDue = card.due();
        CardState goodResult = FsrsScheduler.repeat(card, Rating.GOOD, goodDue, null);

        CardState hardResult = FsrsScheduler.repeat(hardCard, Rating.HARD, hardCard.due(), null);

        long goodInterval = Duration.between(goodDue, goodResult.due()).toDays();
        long hardInterval = Duration.between(hardCard.due(), hardResult.due()).toDays();

        assertThat(hardInterval).isLessThan(goodInterval);
    }

    @Test
    void repeat_easyBoost_easyStabilityGreaterThanGood() {
        CardState card = FsrsScheduler.initNewCard(BASE_TIME);

        card = FsrsScheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), null);

        CardState easyCard = FsrsScheduler.initNewCard(BASE_TIME);
        easyCard = FsrsScheduler.repeat(easyCard, Rating.GOOD, BASE_TIME, null);
        easyCard = FsrsScheduler.repeat(easyCard, Rating.GOOD, easyCard.due(), null);

        card = FsrsScheduler.repeat(card, Rating.GOOD, card.due(), null);
        easyCard = FsrsScheduler.repeat(easyCard, Rating.EASY, easyCard.due(), null);

        assertThat(easyCard.stability()).isGreaterThan(card.stability());
    }
}
