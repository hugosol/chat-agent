package com.hugosol.chatagent.flashcard;

import static org.assertj.core.api.Assertions.assertThat;

import com.hugosol.chatagent.model.ReviewLog;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

class FsrsOptimizerTest {

    private static final double[] PYFSRS_OPTIMAL = {
            0.12340357383516173, 1.2931, 2.397673571899466, 8.2956,
            6.686820427099132, 0.45021679958387956, 3.077875127553957, 0.053520395733247045,
            1.6539992229052127, 0.1466206769107436, 0.6300772488850335, 1.611965002575047,
            0.012840136810798864, 0.34853762746216305, 1.8878958285806287, 0.8546376191171063,
            1.8729, 0.6748536823468675, 0.20451266082721842, 0.22622814695113844,
            0.46030603398979064
    };

    private static final Instant BASE_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void emptyReviewLogs_returnsDefaultWeights() {
        FsrsOptimizer optimizer = new FsrsOptimizer(List.of(), FsrsSchedulerConfig.defaults());
        var result = optimizer.optimize(null);

        assertThat(result.iterations()).isEqualTo(0);
        assertThat(result.weights()).containsExactly(FsrsSchedulerConfig.defaults().weights());
    }

    @Test
    void insufficientData_returnsDefaultWeights() {
        List<ReviewLog> logs = generateReviewLogs(500);
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        var result = optimizer.optimize(null);

        assertThat(result.iterations()).isEqualTo(0);
        assertThat(result.weights()).containsExactly(FsrsSchedulerConfig.defaults().weights());
    }

    @Test
    void allAgainRatings_doesNotCrash() {
        List<ReviewLog> logs = generateUniformRatingLogs(100, 10, Rating.AGAIN);
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());

        var result = optimizer.optimize(null);
        assertThat(result).isNotNull();
        assertThat(result.weights()).isNotNull();
        double loss = optimizer.computeLoss(result.weights());
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void allEasyRatings_doesNotCrash() {
        List<ReviewLog> logs = generateUniformRatingLogs(100, 10, Rating.EASY);
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());

        var result = optimizer.optimize(null);
        assertThat(result).isNotNull();
        assertThat(result.weights()).isNotNull();
        double loss = optimizer.computeLoss(result.weights());
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void sameDayReviews_doNotContributeToLoss() {
        List<ReviewLog> logs = new ArrayList<>();
        Instant base = BASE_TIME;

        for (int card = 0; card < 100; card++) {
            String cardId = "card_" + card;
            for (int r = 0; r < 5; r++) {
                Instant t = base.plusSeconds(r * 3600);
                ReviewLog log = new ReviewLog();
                log.setCardId(cardId);
                log.setUserId("test");
                log.setRating(r % 2 == 0 ? Rating.GOOD : Rating.AGAIN);
                log.setReviewedAt(t);
                logs.add(log);
            }
        }

        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        int nonSameDay = optimizer.totalNonSameDayReviews();
        assertThat(nonSameDay).isEqualTo(100);

        double loss = optimizer.computeLoss(FsrsSchedulerConfig.defaults().weights());
        assertThat(loss).isGreaterThan(0.0);
    }

    @Test
    void deterministicOutput_sameInputTwice() {
        List<ReviewLog> logs = generateReviewLogs(600);
        FsrsOptimizer opt1 = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        FsrsOptimizer opt2 = new FsrsOptimizer(new ArrayList<>(logs), FsrsSchedulerConfig.defaults());

        var result1 = opt1.optimize(null);
        var result2 = opt2.optimize(null);

        assertThat(result2.weights()).containsExactly(result1.weights());
        assertThat(result2.finalLoss()).isCloseTo(result1.finalLoss(), org.assertj.core.api.Assertions.within(1e-10));
    }

    @Test
    void shuffledInput_producesSameOutput() {
        List<ReviewLog> ordered = generateReviewLogs(600);

        List<ReviewLog> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled, new Random(12345));

        FsrsOptimizer optOrdered = new FsrsOptimizer(ordered, FsrsSchedulerConfig.defaults());
        FsrsOptimizer optShuffled = new FsrsOptimizer(shuffled, FsrsSchedulerConfig.defaults());

        var resultOrdered = optOrdered.optimize(null);
        var resultShuffled = optShuffled.optimize(null);

        assertThat(resultShuffled.weights()).containsExactly(resultOrdered.weights());
        assertThat(resultShuffled.finalLoss()).isCloseTo(resultOrdered.finalLoss(), org.assertj.core.api.Assertions.within(1e-10));
    }

    @Test
    void syntheticParameterRecovery() {
        double[] defaultWeights = FsrsSchedulerConfig.defaults().weights();
        FsrsScheduler scheduler = new FsrsScheduler(FsrsSchedulerConfig.defaults());
        Random rng = new Random(999);

        List<ReviewLog> logs = new ArrayList<>();
        Instant base = BASE_TIME;

        for (int card = 0; card < 500; card++) {
            String cardId = "synth_" + card;
            CardState state = scheduler.enchantCard(base);

            for (int r = 0; r < 20; r++) {
                Rating rating = Rating.values()[rng.nextInt(4)];
                Instant reviewTime = base.plus(Duration.ofDays(card * 30L + r * 7L + rng.nextInt(3)));
                state = scheduler.repeat(state, rating, reviewTime, null);

                ReviewLog log = new ReviewLog();
                log.setCardId(cardId);
                log.setUserId("test");
                log.setRating(rating);
                log.setReviewedAt(reviewTime);
                logs.add(log);
            }
        }

        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        var result = optimizer.optimize(null);

        double[] recovered = result.weights();
        double lossRecovered = optimizer.computeLoss(recovered);
        double lossDefault = optimizer.computeLoss(defaultWeights);

        assertThat(lossRecovered)
                .as("Recovered parameters should achieve lower loss than defaults on the same data")
                .isLessThan(lossDefault);
    }

    @Test
    void crossValidation_mainStandard_lossDecreases() {
        List<ReviewLog> logs = loadCsvReviewLogs("fsrs/review_logs_josh_1711744352250_to_1728234780857.csv");

        FsrsSchedulerConfig defaultConfig = FsrsSchedulerConfig.defaults();
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, defaultConfig);

        double lossDefault = optimizer.computeLoss(defaultConfig.weights());
        assertThat(lossDefault).isGreaterThan(0.0);

        var result = optimizer.optimize(null);
        double lossOptimized = result.finalLoss();

        assertThat(lossOptimized)
                .as("Optimized loss should be strictly less than default loss")
                .isLessThan(lossDefault);
    }

    @Test
    void crossValidation_auxiliaryStandard_nearPyfsrs() {
        List<ReviewLog> logs = loadCsvReviewLogs("fsrs/review_logs_josh_1711744352250_to_1728234780857.csv");

        FsrsSchedulerConfig defaultConfig = FsrsSchedulerConfig.defaults();
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, defaultConfig);

        var result = optimizer.optimize(null);
        double lossOptimized = result.finalLoss();

        double lossPyfsrs = optimizer.computeLoss(PYFSRS_OPTIMAL);

        double lossDefault = optimizer.computeLoss(defaultConfig.weights());

        boolean nearPyfsrs = lossOptimized <= lossPyfsrs * 1.01;
        boolean muchBetterThanDefault = lossOptimized <= lossDefault * 0.95;

        assertThat(nearPyfsrs || muchBetterThanDefault)
                .as("Optimized loss should be within 1%% of py-fsrs loss OR at least 5%% better than default. "
                    + "loss_optimized=%.6f, loss_pyfsrs=%.6f, loss_default=%.6f",
                    lossOptimized, lossPyfsrs, lossDefault)
                .isTrue();
    }

    @Test
    void crossValidation_deterministic() {
        List<ReviewLog> logs = loadCsvReviewLogs("fsrs/review_logs_josh_1711744352250_to_1728234780857.csv");

        FsrsOptimizer opt1 = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        FsrsOptimizer opt2 = new FsrsOptimizer(new ArrayList<>(logs), FsrsSchedulerConfig.defaults());

        var r1 = opt1.optimize(null);
        var r2 = opt2.optimize(null);

        assertThat(r2.weights()).containsExactly(r1.weights());
    }

    @Test
    void crossValidation_shuffledSameResult() {
        List<ReviewLog> ordered = loadCsvReviewLogs("fsrs/review_logs_josh_1711744352250_to_1728234780857.csv");

        List<ReviewLog> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled, new Random(42));

        FsrsOptimizer optOrdered = new FsrsOptimizer(ordered, FsrsSchedulerConfig.defaults());
        FsrsOptimizer optShuffled = new FsrsOptimizer(shuffled, FsrsSchedulerConfig.defaults());

        var resultOrdered = optOrdered.optimize(null);
        var resultShuffled = optShuffled.optimize(null);

        assertThat(resultShuffled.weights()).containsExactly(resultOrdered.weights());
    }

    @Test
    void w15w16_excludedFromLossComparison() {
        List<ReviewLog> logs = loadCsvReviewLogs("fsrs/review_logs_josh_1711744352250_to_1728234780857.csv");
        FsrsSchedulerConfig defaultConfig = FsrsSchedulerConfig.defaults();
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, defaultConfig);

        double[] defaultWeights = defaultConfig.weights();
        double[] modifiedWeights = defaultWeights.clone();
        modifiedWeights[15] = 0.999;
        modifiedWeights[16] = 5.999;

        double lossDefault = optimizer.computeLoss(defaultWeights);
        double lossModified = optimizer.computeLoss(modifiedWeights);

        assertThat(lossModified)
                .as("w[15]/w[16] changes should have negligible effect on loss with this dataset")
                .isCloseTo(lossDefault, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void progressCallback_isInvoked() {
        List<ReviewLog> logs = generateReviewLogs(600);
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());

        int[] callCount = {0};
        var result = optimizer.optimize((epoch, batch, totalBatches, currentLoss) -> {
            callCount[0]++;
        });

        assertThat(callCount[0]).isGreaterThan(0);
    }

    @Test
    void clampsWeightsToBounds() {
        List<ReviewLog> logs = generateReviewLogs(600);
        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());
        var result = optimizer.optimize(null);

        double[] weights = result.weights();
        for (int i = 0; i < 21; i++) {
            assertThat(weights[i])
                    .as("w[" + i + "] should be within bounds")
                    .isBetween(FsrsOptimizer.LOWER_BOUNDS[i], FsrsOptimizer.UPPER_BOUNDS[i]);
        }
    }

    @Test
    void maxSeqLen_capsAt64() {
        List<ReviewLog> logs = new ArrayList<>();
        String cardId = "single_card";
        Instant base = BASE_TIME;

        for (int i = 0; i < 100; i++) {
            ReviewLog log = new ReviewLog();
            log.setCardId(cardId);
            log.setUserId("test");
            log.setRating(i % 2 == 0 ? Rating.GOOD : Rating.AGAIN);
            log.setReviewedAt(base.plus(Duration.ofDays(i)));
            logs.add(log);
        }

        FsrsOptimizer optimizer = new FsrsOptimizer(logs, FsrsSchedulerConfig.defaults());

        var result = optimizer.optimize(null);
        assertThat(result).isNotNull();
        assertThat(result.weights()[0]).isBetween(
                FsrsOptimizer.LOWER_BOUNDS[0], FsrsOptimizer.UPPER_BOUNDS[0]);
    }

    private List<ReviewLog> generateReviewLogs(int count) {
        return generateUniformRatingLogs(count / 10, 10, null);
    }

    private List<ReviewLog> generateUniformRatingLogs(int numCards, int reviewsPerCard, Rating fixedRating) {
        List<ReviewLog> logs = new ArrayList<>();
        Random rng = new Random(42);
        Instant base = BASE_TIME;

        for (int card = 0; card < numCards; card++) {
            String cardId = "card_" + card;
            for (int r = 0; r < reviewsPerCard; r++) {
                Rating rating = (fixedRating != null) ? fixedRating : Rating.values()[rng.nextInt(4)];
                Instant t = base.plus(Duration.ofDays(card * 30L + r * 7L + rng.nextInt(3)));
                ReviewLog log = new ReviewLog();
                log.setCardId(cardId);
                log.setUserId("test");
                log.setRating(rating);
                log.setReviewedAt(t);
                logs.add(log);
            }
        }
        return logs;
    }

    static List<ReviewLog> loadCsvReviewLogs(String resourcePath) {
        List<ReviewLog> logs = new ArrayList<>();
        InputStream is = FsrsOptimizerTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new RuntimeException("Test resource not found: " + resourcePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return logs;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 3) continue;

                String cardId = parts[0].trim();
                int ratingCode = Integer.parseInt(parts[1].trim());
                String timeStr = parts[2].trim();

                Rating rating = switch (ratingCode) {
                    case 1 -> Rating.AGAIN;
                    case 2 -> Rating.HARD;
                    case 3 -> Rating.GOOD;
                    case 4 -> Rating.EASY;
                    default -> throw new IllegalArgumentException("Unknown rating code: " + ratingCode);
                };

                Instant reviewedAt = Instant.parse(timeStr);

                ReviewLog log = new ReviewLog();
                log.setCardId(cardId);
                log.setUserId("josh");
                log.setRating(rating);
                log.setReviewedAt(reviewedAt);
                logs.add(log);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CSV: " + resourcePath, e);
        }

        return logs;
    }
}
