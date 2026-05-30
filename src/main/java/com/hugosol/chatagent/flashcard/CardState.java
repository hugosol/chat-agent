package com.hugosol.chatagent.flashcard;

import java.time.Instant;

public record CardState(
        double stability,
        double difficulty,
        int state,
        int step,
        Instant due,
        int reps,
        int lapses,
        Instant lastReview,
        double elapsedDays,
        boolean hasStability) {

    public static final int STATE_LEARNING = 1;
    public static final int STATE_REVIEW = 2;
    public static final int STATE_RELEARNING = 3;

    static CardState forInitialLearning(Instant now) {
        return new CardState(0.0, 0.0, STATE_LEARNING, 0, now, 0, 0, null, 0.0, false);
    }
}
