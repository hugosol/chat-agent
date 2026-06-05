package com.hugosol.chatagent.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

class FsrsSchedulerTest {

    private static final FsrsScheduler scheduler = new FsrsScheduler(FsrsSchedulerConfig.defaults());

    private static final Instant BASE_TIME = Instant.parse("2022-11-29T12:30:00Z");

    private static final double TOLERANCE = 1e-4;

    @Test
    void initNewCard_createsLearningCardWithoutStability() {
        Instant now = BASE_TIME;

        CardState state = scheduler.initNewCard(now);

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
        CardState card = scheduler.initNewCard(BASE_TIME);

        CardState againResult = scheduler.repeat(card, Rating.AGAIN, BASE_TIME, null);
        CardState hardResult = scheduler.repeat(card, Rating.HARD, BASE_TIME, null);
        CardState goodResult = scheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        CardState easyResult = scheduler.repeat(card, Rating.EASY, BASE_TIME, null);

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
        CardState card = scheduler.initNewCard(BASE_TIME);
        double r = scheduler.retrievability(card, BASE_TIME);
        assertThat(r).isEqualTo(0.0);
    }

    @Test
    void retrievability_afterReview_returnsReasonableValue() {
        CardState card = scheduler.initNewCard(BASE_TIME);
        card = scheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = scheduler.repeat(card, Rating.GOOD, card.due(), null);

        double r = scheduler.retrievability(card, card.due());
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

        CardState card = scheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < ratings.length; i++) {
            card = scheduler.repeat(card, ratings[i], now, null);
            int ivl = (int) Duration.between(card.lastReview(), card.due()).toDays();
            assertThat(ivl).as("interval " + i).isEqualTo(expectedIntervals[i]);
            now = card.due();
        }
    }

    @Test
    void repeat_6Reviews_producesPreciseStabilityAndDifficulty() {
        Rating[] ratings = {Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD};
        int[] advanceDays = {0, 0, 1, 3, 8, 21};

        CardState card = scheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < ratings.length; i++) {
            now = now.plus(Duration.ofDays(advanceDays[i]));
            card = scheduler.repeat(card, ratings[i], now, null);
        }

        assertThat(card.stability()).isCloseTo(53.62691, within(TOLERANCE));
        assertThat(card.difficulty()).isCloseTo(6.3574867, within(TOLERANCE));
    }

    @Test
    void repeat_10Easy_hitsDifficultyFloor() {
        CardState card = scheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < 10; i++) {
            now = now.plus(Duration.ofSeconds(1));
            card = scheduler.repeat(card, Rating.EASY, now, null);
        }

        assertThat(card.difficulty()).isEqualTo(1.0);
    }

    @Test
    void repeat_1000Again_stabilityAtLeastMinimum() {
        CardState card = scheduler.initNewCard(BASE_TIME);
        Instant now = BASE_TIME;

        for (int i = 0; i < 1000; i++) {
            now = now.plus(Duration.ofDays(1));
            card = scheduler.repeat(card, Rating.AGAIN, now, null);
            assertThat(card.stability()).isGreaterThanOrEqualTo(FsrsScheduler.STABILITY_MIN);
        }
    }

    @Test
    void repeat_fuzzDeterministic_seed42GivesConsistentInterval() {
        AleaPrng alea = new AleaPrng(42);
        CardState card = scheduler.initNewCard(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        card = scheduler.repeat(card, Rating.GOOD, now, alea::next);
        card = scheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        Instant prevDue = card.due();
        card = scheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        int interval = (int) Duration.between(prevDue, card.due()).toDays();

        assertThat(interval).isEqualTo(12);
    }

    @Test
    void repeat_fuzzDeterministic_seed12345GivesConsistentInterval() {
        AleaPrng alea = new AleaPrng(12345);
        CardState card = scheduler.initNewCard(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        card = scheduler.repeat(card, Rating.GOOD, now, alea::next);
        card = scheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        Instant prevDue = card.due();
        card = scheduler.repeat(card, Rating.GOOD, card.due(), alea::next);
        int interval = (int) Duration.between(prevDue, card.due()).toDays();

        assertThat(interval).isEqualTo(11);
    }

    @Test
    void repeat_hardModifier_hardIntervalShorterThanGood() {
        CardState card = scheduler.initNewCard(BASE_TIME);

        card = scheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = scheduler.repeat(card, Rating.GOOD, card.due(), null);

        CardState hardCard = scheduler.initNewCard(BASE_TIME);
        hardCard = scheduler.repeat(hardCard, Rating.GOOD, BASE_TIME, null);
        hardCard = scheduler.repeat(hardCard, Rating.GOOD, hardCard.due(), null);

        Instant goodDue = card.due();
        CardState goodResult = scheduler.repeat(card, Rating.GOOD, goodDue, null);

        CardState hardResult = scheduler.repeat(hardCard, Rating.HARD, hardCard.due(), null);

        long goodInterval = Duration.between(goodDue, goodResult.due()).toDays();
        long hardInterval = Duration.between(hardCard.due(), hardResult.due()).toDays();

        assertThat(hardInterval).isLessThan(goodInterval);
    }

    @Test
    void repeat_easyBoost_easyStabilityGreaterThanGood() {
        CardState card = scheduler.initNewCard(BASE_TIME);

        card = scheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = scheduler.repeat(card, Rating.GOOD, card.due(), null);

        CardState easyCard = scheduler.initNewCard(BASE_TIME);
        easyCard = scheduler.repeat(easyCard, Rating.GOOD, BASE_TIME, null);
        easyCard = scheduler.repeat(easyCard, Rating.GOOD, easyCard.due(), null);

        card = scheduler.repeat(card, Rating.GOOD, card.due(), null);
        easyCard = scheduler.repeat(easyCard, Rating.EASY, easyCard.due(), null);

        assertThat(easyCard.stability()).isGreaterThan(card.stability());
    }

    @Test
    void noLearningSteps_newCardGraduatesImmediately() {
        FsrsSchedulerConfig config = new FsrsSchedulerConfig(
                FsrsSchedulerConfig.defaults().weights(), 0.9,
                new java.time.Duration[0],
                FsrsSchedulerConfig.defaults().relearningSteps(),
                36500, true, true);
        FsrsScheduler s = new FsrsScheduler(config);

        CardState card = s.initNewCard(BASE_TIME);
        CardState result = s.repeat(card, Rating.GOOD, BASE_TIME, null);

        assertThat(result.state()).isEqualTo(CardState.STATE_REVIEW);
        assertThat(result.step()).isEqualTo(-1);
    }

    @Test
    void noRelearningSteps_againDoesNotEnterRelearning() {
        FsrsSchedulerConfig config = new FsrsSchedulerConfig(
                FsrsSchedulerConfig.defaults().weights(), 0.9,
                FsrsSchedulerConfig.defaults().learningSteps(),
                new java.time.Duration[0],
                36500, true, true);
        FsrsScheduler s = new FsrsScheduler(config);

        CardState card = s.initNewCard(BASE_TIME);
        card = s.repeat(card, Rating.GOOD, BASE_TIME, null);
        card = s.repeat(card, Rating.GOOD, card.due(), null);
        CardState result = s.repeat(card, Rating.AGAIN, card.due(), null);

        assertThat(result.state()).isNotEqualTo(CardState.STATE_RELEARNING);
    }

    @Test
    void differentWeights_produceDifferentResult() {
        double[] altWeights = FsrsSchedulerConfig.defaults().weights().clone();
        altWeights[0] = 0.5;
        FsrsSchedulerConfig altConfig = new FsrsSchedulerConfig(
                altWeights, 0.9,
                FsrsSchedulerConfig.defaults().learningSteps(),
                FsrsSchedulerConfig.defaults().relearningSteps(),
                36500, true, true);
        FsrsScheduler altS = new FsrsScheduler(altConfig);

        CardState card = scheduler.initNewCard(BASE_TIME);
        CardState defResult = scheduler.repeat(card, Rating.AGAIN, BASE_TIME, null);

        CardState altCard = altS.initNewCard(BASE_TIME);
        CardState altResult = altS.repeat(altCard, Rating.AGAIN, BASE_TIME, null);

        assertThat(altResult.stability()).isNotEqualTo(defResult.stability());
    }

    @Test
    void disableFuzz_producesNoIntervalVariation() {
        FsrsSchedulerConfig config = new FsrsSchedulerConfig(
                FsrsSchedulerConfig.defaults().weights(), 0.9,
                FsrsSchedulerConfig.defaults().learningSteps(),
                FsrsSchedulerConfig.defaults().relearningSteps(),
                36500, false, true);
        FsrsScheduler noFuzz = new FsrsScheduler(config);

        AleaPrng alea = new AleaPrng(42);
        CardState card = noFuzz.initNewCard(BASE_TIME);
        card = noFuzz.repeat(card, Rating.GOOD, BASE_TIME, alea::next);
        card = noFuzz.repeat(card, Rating.GOOD, card.due(), alea::next);
        Instant prevDue = card.due();
        card = noFuzz.repeat(card, Rating.GOOD, card.due(), alea::next);
        int ivl = (int) java.time.Duration.between(prevDue, card.due()).toDays();

        assertThat(ivl).isEqualTo((int) Math.round(card.stability()));
    }

    @Test
    void customMaximumInterval_capsInterval() {
        FsrsSchedulerConfig config = new FsrsSchedulerConfig(
                FsrsSchedulerConfig.defaults().weights(), 0.9,
                FsrsSchedulerConfig.defaults().learningSteps(),
                FsrsSchedulerConfig.defaults().relearningSteps(),
                5, true, true);
        FsrsScheduler capped = new FsrsScheduler(config);

        CardState card = capped.initNewCard(BASE_TIME);
        card = capped.repeat(card, Rating.EASY, BASE_TIME, null);
        card = capped.repeat(card, Rating.EASY, card.due(), null);
        card = capped.repeat(card, Rating.EASY, card.due(), null);

        long ivl = java.time.Duration.between(card.lastReview(), card.due()).toDays();
        assertThat(ivl).isLessThanOrEqualTo(5);
    }

    @Test
    void forgettingCurve_returnsReasonableValue() {
        double r = FsrsScheduler.forgettingCurve(0, 10, -0.1542);
        assertThat(r).isCloseTo(1.0, within(0.001));

        double r2 = FsrsScheduler.forgettingCurve(10, 10, -0.1542);
        assertThat(r2).isLessThan(1.0);
        assertThat(r2).isGreaterThan(0.0);
    }

    @Test
    void stateNew_constantIsZero() {
        assertThat(FsrsScheduler.STATE_NEW).isEqualTo(0);
    }

    @Test
    void retrievability_isPublicInstanceMethod() {
        CardState card = scheduler.initNewCard(BASE_TIME);
        card = scheduler.repeat(card, Rating.GOOD, BASE_TIME, null);
        double r = scheduler.retrievability(card, card.due());
        assertThat(r).isGreaterThan(0.8);
    }

    @Test
    void createInitState_isStaticAndReturnsNewCard() {
        CardState state = FsrsScheduler.createInitState(BASE_TIME);
        assertThat(state.state()).isEqualTo(FsrsScheduler.STATE_NEW);
        assertThat(state.stability()).isCloseTo(2.5, within(TOLERANCE));
        assertThat(state.difficulty()).isCloseTo(0.0, within(TOLERANCE));
    }
}
