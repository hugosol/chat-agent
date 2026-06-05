package com.hugosol.chatagent.flashcard;

import com.hugosol.chatagent.dto.OptimizeResult;
import com.hugosol.chatagent.model.ReviewLog;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FsrsOptimizer {

    private static final double INITIAL_LR = 4e-2;
    private static final double BETA1 = 0.9;
    private static final double BETA2 = 0.999;
    private static final double EPSILON = 1e-8;
    private static final int NUM_EPOCHS = 5;
    private static final int BATCH_SIZE = 512;
    private static final int MAX_SEQ_LEN = 64;
    private static final double GRAD_STEP = 1e-4;
    private static final long RANDOM_SEED = 42;
    private static final int MIN_REVIEWS = 512;

    static final double[] LOWER_BOUNDS = {
            0.001, 0.001, 0.001, 0.001,
            1.0, 0.001, 0.001, 0.001,
            0.0, 0.0, 0.001, 0.001,
            0.001, 0.001, 0.0, 0.0,
            1.0, 0.0, 0.0, 0.0,
            0.1
    };

    static final double[] UPPER_BOUNDS = {
            100.0, 100.0, 100.0, 100.0,
            10.0, 4.0, 4.0, 0.75,
            4.5, 0.8, 3.5, 5.0,
            0.25, 0.9, 4.0, 1.0,
            6.0, 2.0, 2.0, 0.8,
            0.8
    };

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int epoch, int batch, int totalBatches, double currentLoss);
    }

    private final FsrsSchedulerConfig baseConfig;
    private final List<CardSequence> cardSequences;
    private final int totalNonSameDayReviews;
    private final double[] defaultWeights;

    static class ReviewEntry {
        final Rating rating;
        final Instant reviewedAt;
        final boolean sameDay;
        final int recallLabel;

        ReviewEntry(Rating rating, Instant reviewedAt, boolean sameDay) {
            this.rating = rating;
            this.reviewedAt = reviewedAt;
            this.sameDay = sameDay;
            this.recallLabel = (rating == Rating.AGAIN) ? 0 : 1;
        }
    }

    static class CardSequence {
        final String cardId;
        final List<ReviewEntry> entries;

        CardSequence(String cardId, List<ReviewEntry> entries) {
            this.cardId = cardId;
            this.entries = entries;
        }
    }

    public FsrsOptimizer(List<ReviewLog> reviewLogs, FsrsSchedulerConfig config) {
        this.baseConfig = config;
        this.defaultWeights = config.weights();

        Map<String, List<ReviewLog>> grouped = new LinkedHashMap<>();
        for (ReviewLog log : reviewLogs) {
            grouped.computeIfAbsent(log.getCardId(), k -> new ArrayList<>()).add(log);
        }

        List<CardSequence> sequences = new ArrayList<>();
        int nonSameDayCount = 0;

        for (Map.Entry<String, List<ReviewLog>> entry : grouped.entrySet()) {
            String cardId = entry.getKey();
            List<ReviewLog> logs = new ArrayList<>(entry.getValue());
            logs.sort(Comparator.comparing(ReviewLog::getReviewedAt));

            if (logs.size() > MAX_SEQ_LEN) {
                logs = logs.subList(0, MAX_SEQ_LEN);
            }

            List<ReviewEntry> reviewEntries = new ArrayList<>();
            Instant prevTime = null;

            for (ReviewLog log : logs) {
                boolean sameDay = false;
                if (prevTime != null) {
                    double elapsedDays = Duration.between(prevTime, log.getReviewedAt()).getSeconds() / 86400.0;
                    sameDay = elapsedDays < 1.0;
                }
                reviewEntries.add(new ReviewEntry(log.getRating(), log.getReviewedAt(), sameDay));
                if (!sameDay) {
                    nonSameDayCount++;
                }
                prevTime = log.getReviewedAt();
            }
            sequences.add(new CardSequence(cardId, reviewEntries));
        }

        sequences.sort(Comparator.comparing(s -> s.cardId));

        this.cardSequences = sequences;
        this.totalNonSameDayReviews = nonSameDayCount;
    }

    public int totalNonSameDayReviews() {
        return totalNonSameDayReviews;
    }

    public double[] defaultWeights() {
        return defaultWeights.clone();
    }

    public double computeLoss(double[] weights) {
        return computeLossForCards(cardSequences, weights);
    }

    public OptimizeResult optimize(ProgressCallback callback) {
        long startTime = System.currentTimeMillis();

        if (totalNonSameDayReviews < MIN_REVIEWS) {
            long duration = System.currentTimeMillis() - startTime;
            return new OptimizeResult(defaultWeights, 0.0, 0, duration);
        }

        int totalBatches = (int) Math.ceil((double) totalNonSameDayReviews / BATCH_SIZE);
        if (totalBatches == 0) {
            totalBatches = 1;
        }

        int tMax = totalBatches * NUM_EPOCHS;

        double[] w = defaultWeights.clone();
        double[] m = new double[21];
        double[] v = new double[21];
        int t = 0;

        Random rng = new Random(RANDOM_SEED);

        for (int epoch = 0; epoch < NUM_EPOCHS; epoch++) {
            List<CardSequence> shuffled = new ArrayList<>(cardSequences);
            Collections.shuffle(shuffled, rng);

            int batchStart = 0;
            int batchIndex = 0;

            while (batchStart < shuffled.size()) {
                List<CardSequence> batchCards = new ArrayList<>();
                int reviewCount = 0;

                for (int i = batchStart; i < shuffled.size() && reviewCount < BATCH_SIZE; i++) {
                    CardSequence seq = shuffled.get(i);
                    batchCards.add(seq);
                    reviewCount += seq.entries.size();
                    batchStart = i + 1;
                }

                double[] grad = computeCardBatchGradient(batchCards, w);

                for (int i = 0; i < 21; i++) {
                    m[i] = BETA1 * m[i] + (1 - BETA1) * grad[i];
                    v[i] = BETA2 * v[i] + (1 - BETA2) * grad[i] * grad[i];

                    double mHat = m[i] / (1 - Math.pow(BETA1, t + 1));
                    double vHat = v[i] / (1 - Math.pow(BETA2, t + 1));
                    double lr = cosineAnnealingLR(t, tMax);

                    w[i] -= lr * mHat / (Math.sqrt(vHat) + EPSILON);
                    w[i] = clamp(w[i], LOWER_BOUNDS[i], UPPER_BOUNDS[i]);
                }

                t++;

                if (callback != null) {
                    double currentLoss = computeLossForCards(shuffled, w);
                    callback.onProgress(epoch, batchIndex, totalBatches, currentLoss);
                }
                batchIndex++;
            }
        }

        double finalLoss = computeLossForCards(cardSequences, w);
        long duration = System.currentTimeMillis() - startTime;

        return new OptimizeResult(w, finalLoss, t, duration);
    }

    double computeLossForCards(List<CardSequence> cards, double[] weights) {
        FsrsSchedulerConfig config = configWithWeights(weights);
        FsrsScheduler scheduler = new FsrsScheduler(config);

        double totalLoss = 0.0;
        int count = 0;

        for (CardSequence seq : cards) {
            if (seq.entries.isEmpty()) continue;

            CardState card = scheduler.enchantCard(seq.entries.get(0).reviewedAt);

            for (ReviewEntry entry : seq.entries) {
                if (!entry.sameDay) {
                    double r = scheduler.retrievability(card, entry.reviewedAt);
                    r = clamp(r, 1e-10, 1 - 1e-10);
                    totalLoss += -(entry.recallLabel * Math.log(r) + (1 - entry.recallLabel) * Math.log(1 - r));
                    count++;
                }
                card = scheduler.repeat(card, entry.rating, entry.reviewedAt, null);
            }
        }

        return count > 0 ? totalLoss / count : 0.0;
    }

    private double[] computeCardBatchGradient(List<CardSequence> batchCards, double[] weights) {
        double[] grad = new double[21];

        for (int i = 0; i < 21; i++) {
            double[] wPlus = weights.clone();
            wPlus[i] = clamp(wPlus[i] + GRAD_STEP, LOWER_BOUNDS[i], UPPER_BOUNDS[i]);

            double[] wMinus = weights.clone();
            wMinus[i] = clamp(wMinus[i] - GRAD_STEP, LOWER_BOUNDS[i], UPPER_BOUNDS[i]);

            double lossPlus = computeLossForCards(batchCards, wPlus);
            double lossMinus = computeLossForCards(batchCards, wMinus);

            grad[i] = (lossPlus - lossMinus) / (2 * GRAD_STEP);
        }

        return grad;
    }

    private FsrsSchedulerConfig configWithWeights(double[] weights) {
        return new FsrsSchedulerConfig(
                weights,
                baseConfig.desiredRetention(),
                baseConfig.learningSteps(),
                baseConfig.relearningSteps(),
                baseConfig.maximumInterval(),
                baseConfig.enableFuzz(),
                baseConfig.enableShortTerm()
        );
    }

    private static double cosineAnnealingLR(int t, int tMax) {
        return 0.5 * INITIAL_LR * (1 + Math.cos(Math.PI * t / tMax));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
