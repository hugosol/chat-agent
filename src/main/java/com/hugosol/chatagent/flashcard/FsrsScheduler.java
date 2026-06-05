package com.hugosol.chatagent.flashcard;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

public class FsrsScheduler {

    public static final double STABILITY_MIN = 0.001;
    public static final int STATE_NEW = 0;

    private static final double[][] FUZZ_RANGES = {
            {2.5, 7.0, 0.15},
            {7.0, 20.0, 0.1},
            {20.0, Double.POSITIVE_INFINITY, 0.05}
    };

    private final FsrsSchedulerConfig config;
    private final double decay;
    private final double factor;

    public FsrsScheduler(FsrsSchedulerConfig config) {
        this.config = config;
        this.decay = -config.weights()[20];
        this.factor = Math.pow(config.desiredRetention(), 1.0 / decay) - 1;
    }

    public static CardState createInitState(Instant now) {
        return new CardState(2.5, 0.0, STATE_NEW, -1, now, 0, 0, null, 0.0, true);
    }

    public CardState initNewCard(Instant now) {
        return CardState.forInitialLearning(now);
    }

    public CardState repeat(CardState card, Rating rating, Instant now, DoubleSupplier fuzzSource) {
        double stability = card.stability();
        double difficulty = card.difficulty();
        int reps = card.reps() + 1;
        int lapses = card.lapses();
        int state = card.state();
        int step = card.step();
        boolean hasStability = card.hasStability();
        Instant lastReview = now;

        long elapsedSeconds = 0;
        if (card.lastReview() != null) {
            elapsedSeconds = Duration.between(card.lastReview(), now).getSeconds();
        }
        boolean sameDay = elapsedSeconds < 86400 && card.lastReview() != null;

        if (!hasStability) {
            stability = initialStability(rating);
            difficulty = initialDifficulty(rating, true);
            hasStability = true;
        } else if (config.enableShortTerm() && (state == CardState.STATE_LEARNING || state == CardState.STATE_RELEARNING)) {
            if (sameDay) {
                stability = shortTermStability(stability, rating);
            } else {
                stability = nextStability(difficulty, stability,
                        retrievability(stability, card.lastReview(), now), rating);
            }
            difficulty = nextDifficulty(difficulty, rating);
        } else if (config.enableShortTerm() && state == CardState.STATE_REVIEW) {
            if (sameDay) {
                stability = shortTermStability(stability, rating);
            } else {
                stability = nextStability(difficulty, stability,
                        retrievability(stability, card.lastReview(), now), rating);
            }
            difficulty = nextDifficulty(difficulty, rating);
        } else if (!config.enableShortTerm() && (state == CardState.STATE_LEARNING || state == CardState.STATE_RELEARNING || state == CardState.STATE_REVIEW)) {
            stability = nextStability(difficulty, stability,
                    retrievability(stability, card.lastReview(), now), rating);
            difficulty = nextDifficulty(difficulty, rating);
        }

        if (rating == Rating.AGAIN && card.state() == CardState.STATE_REVIEW) {
            lapses++;
        }

        Instant due;
        long intervalSeconds;

        if (state == CardState.STATE_LEARNING) {
            int learningStepsCount = config.learningSteps().length;
            if (learningStepsCount == 0 || (step >= learningStepsCount
                    && (rating == Rating.HARD || rating == Rating.GOOD || rating == Rating.EASY))) {
                state = CardState.STATE_REVIEW;
                step = -1;
                intervalSeconds = nextIntervalDays(stability) * 86400L;
            } else {
                switch (rating) {
                    case AGAIN:
                        step = 0;
                        intervalSeconds = learningStepSeconds(step);
                        break;
                    case HARD:
                        if (step == 0 && learningStepsCount == 1) {
                            intervalSeconds = (long) (learningStepSeconds(0) * 1.5);
                        } else if (step == 0 && learningStepsCount >= 2) {
                            intervalSeconds = (learningStepSeconds(0) + learningStepSeconds(1)) / 2;
                        } else {
                            intervalSeconds = learningStepSeconds(step);
                        }
                        break;
                    case GOOD:
                        if (step + 1 == learningStepsCount) {
                            state = CardState.STATE_REVIEW;
                            step = -1;
                            intervalSeconds = nextIntervalDays(stability) * 86400L;
                        } else {
                            step++;
                            intervalSeconds = learningStepSeconds(step);
                        }
                        break;
                    case EASY:
                        state = CardState.STATE_REVIEW;
                        step = -1;
                        intervalSeconds = nextIntervalDays(stability) * 86400L;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown rating: " + rating);
                }
            }
        } else if (state == CardState.STATE_REVIEW) {
            int relearningStepsCount = config.relearningSteps().length;
            switch (rating) {
                case AGAIN:
                    if (relearningStepsCount == 0) {
                        intervalSeconds = nextIntervalDays(stability) * 86400L;
                    } else {
                        state = CardState.STATE_RELEARNING;
                        step = 0;
                        intervalSeconds = relearningStepSeconds(0);
                    }
                    break;
                case HARD:
                case GOOD:
                case EASY:
                    intervalSeconds = nextIntervalDays(stability) * 86400L;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rating: " + rating);
            }
        } else {
            int relearningStepsCount = config.relearningSteps().length;
            if (relearningStepsCount == 0 || (step >= relearningStepsCount
                    && (rating == Rating.HARD || rating == Rating.GOOD || rating == Rating.EASY))) {
                state = CardState.STATE_REVIEW;
                step = -1;
                intervalSeconds = nextIntervalDays(stability) * 86400L;
            } else {
                switch (rating) {
                    case AGAIN:
                        step = 0;
                        intervalSeconds = relearningStepSeconds(0);
                        break;
                    case HARD:
                        if (step == 0 && relearningStepsCount == 1) {
                            intervalSeconds = (long) (relearningStepSeconds(0) * 1.5);
                        } else {
                            intervalSeconds = relearningStepSeconds(step);
                        }
                        break;
                    case GOOD:
                        if (step + 1 == relearningStepsCount) {
                            state = CardState.STATE_REVIEW;
                            step = -1;
                            intervalSeconds = nextIntervalDays(stability) * 86400L;
                        } else {
                            step++;
                            intervalSeconds = relearningStepSeconds(step);
                        }
                        break;
                    case EASY:
                        state = CardState.STATE_REVIEW;
                        step = -1;
                        intervalSeconds = nextIntervalDays(stability) * 86400L;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown rating: " + rating);
                }
            }
        }

        if (config.enableFuzz() && fuzzSource != null && state == CardState.STATE_REVIEW && intervalSeconds >= (long) (2.5 * 86400)) {
            intervalSeconds = getFuzzedInterval(intervalSeconds / 86400.0, fuzzSource.getAsDouble()) * 86400L;
        }

        due = now.plusSeconds(intervalSeconds);
        double elapsedDays = card.lastReview() != null
                ? Duration.between(card.lastReview(), now).getSeconds() / 86400.0
                : 0.0;

        return new CardState(stability, difficulty, state, step, due, reps, lapses, lastReview, elapsedDays, hasStability);
    }

    public Map<Rating, CardState> preview(CardState card, Instant now) {
        Map<Rating, CardState> result = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            result.put(rating, repeat(card, rating, now, null));
        }
        return result;
    }

    private long learningStepSeconds(int step) {
        Duration[] steps = config.learningSteps();
        if (steps.length == 0) return 0;
        if (step < 0) return steps[0].getSeconds();
        if (step >= steps.length) return steps[steps.length - 1].getSeconds();
        return steps[step].getSeconds();
    }

    private long relearningStepSeconds(int step) {
        Duration[] steps = config.relearningSteps();
        if (steps.length == 0) return 0;
        if (step < 0) return steps[0].getSeconds();
        if (step >= steps.length) return steps[steps.length - 1].getSeconds();
        return steps[step].getSeconds();
    }

    private double initialStability(Rating rating) {
        return Math.max(config.weights()[rating.pyValue() - 1], STABILITY_MIN);
    }

    private double initialDifficulty(Rating rating, boolean clamp) {
        double[] w = config.weights();
        double d = w[4] - Math.exp(w[5] * (rating.pyValue() - 1)) + 1;
        if (clamp) d = clampDifficulty(d);
        return d;
    }

    private double clampDifficulty(double d) {
        return Math.max(1.0, Math.min(10.0, d));
    }

    private double clampStability(double s) {
        return Math.max(STABILITY_MIN, s);
    }

    public double retrievability(CardState card, Instant now) {
        if (card.lastReview() == null) return 0;
        return retrievability(card.stability(), card.lastReview(), now);
    }

    private double retrievability(double stability, Instant lastReview, Instant now) {
        if (lastReview == null) return 0;
        double elapsedDays = Duration.between(lastReview, now).getSeconds() / 86400.0;
        return Math.pow(1 + factor * Math.max(0, elapsedDays) / stability, decay);
    }

    public static double forgettingCurve(double elapsedDays, double stability, double decay) {
        double factor = Math.pow(0.9, 1.0 / decay) - 1;
        return Math.pow(1 + factor * Math.max(0, elapsedDays) / stability, decay);
    }

    private int nextIntervalDays(double stability) {
        double ivl = (stability / factor) * (Math.pow(config.desiredRetention(), 1.0 / decay) - 1);
        int days = (int) Math.round(ivl);
        days = Math.max(days, 1);
        days = Math.min(days, config.maximumInterval());
        return days;
    }

    private double shortTermStability(double stability, Rating rating) {
        double[] w = config.weights();
        double increase = Math.exp(w[17] * (rating.pyValue() - 3 + w[18])) * Math.pow(stability, -w[19]);
        if (rating == Rating.GOOD || rating == Rating.EASY) {
            increase = Math.max(increase, 1.0);
        }
        return clampStability(stability * increase);
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double[] w = config.weights();
        double initEasy = initialDifficulty(Rating.EASY, false);
        double delta = -(w[6] * (rating.pyValue() - 3));
        double linearDamping = (10.0 - difficulty) * delta / 9.0;
        double arg2 = difficulty + linearDamping;
        double nextD = w[7] * initEasy + (1 - w[7]) * arg2;
        return clampDifficulty(nextD);
    }

    private double nextStability(double difficulty, double stability, double retrievability, Rating rating) {
        if (rating == Rating.AGAIN) {
            return clampStability(nextForgetStability(difficulty, stability, retrievability));
        }
        return clampStability(nextRecallStability(difficulty, stability, retrievability, rating));
    }

    private double nextForgetStability(double difficulty, double stability, double retrievability) {
        double[] w = config.weights();
        double longTerm = w[11]
                * Math.pow(difficulty, -w[12])
                * (Math.pow(stability + 1, w[13]) - 1)
                * Math.exp((1 - retrievability) * w[14]);
        double shortTerm = stability / Math.exp(w[17] * w[18]);
        return Math.min(longTerm, shortTerm);
    }

    private double nextRecallStability(double difficulty, double stability, double retrievability, Rating rating) {
        double[] w = config.weights();
        double hardPenalty = rating == Rating.HARD ? w[15] : 1;
        double easyBonus = rating == Rating.EASY ? w[16] : 1;
        return stability * (1
                + Math.exp(w[8])
                * (11 - difficulty)
                * Math.pow(stability, -w[9])
                * (Math.exp((1 - retrievability) * w[10]) - 1)
                * hardPenalty
                * easyBonus);
    }

    private long getFuzzedInterval(double intervalDays, double randomValue) {
        if (intervalDays < 2.5) return (long) Math.round(intervalDays);

        double delta = 1.0;
        for (double[] range : FUZZ_RANGES) {
            delta += range[2] * Math.max(Math.min(intervalDays, range[1]) - range[0], 0.0);
        }

        int minIvl = (int) Math.round(intervalDays - delta);
        int maxIvl = (int) Math.round(intervalDays + delta);
        minIvl = Math.max(2, minIvl);
        maxIvl = Math.min(maxIvl, config.maximumInterval());
        minIvl = Math.min(minIvl, maxIvl);

        double fuzzed = randomValue * (maxIvl - minIvl + 1) + minIvl;
        return (long) Math.min(Math.round(fuzzed), config.maximumInterval());
    }
}
