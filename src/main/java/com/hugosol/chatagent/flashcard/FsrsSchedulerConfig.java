package com.hugosol.chatagent.flashcard;

import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.UserPreferences;

import java.time.Duration;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record FsrsSchedulerConfig(
        double[] weights,
        double desiredRetention,
        Duration[] learningSteps,
        Duration[] relearningSteps,
        int maximumInterval,
        boolean enableFuzz,
        boolean enableShortTerm) {

    private static final Logger log = LoggerFactory.getLogger(FsrsSchedulerConfig.class);

    private static final double[] DEFAULT_WEIGHTS = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    };

    private static final Duration[] DEFAULT_LEARNING_STEPS = {
            Duration.ofMinutes(1), Duration.ofMinutes(10)
    };

    private static final Duration[] DEFAULT_RELEARNING_STEPS = {
            Duration.ofMinutes(10)
    };

    public FsrsSchedulerConfig {
        weights = weights.clone();
        learningSteps = learningSteps.clone();
        relearningSteps = relearningSteps.clone();
    }

    @Override
    public double[] weights() {
        return weights.clone();
    }

    @Override
    public Duration[] learningSteps() {
        return learningSteps.clone();
    }

    @Override
    public Duration[] relearningSteps() {
        return relearningSteps.clone();
    }

    public static FsrsSchedulerConfig defaults() {
        return new FsrsSchedulerConfig(
                DEFAULT_WEIGHTS,
                0.9,
                DEFAULT_LEARNING_STEPS,
                DEFAULT_RELEARNING_STEPS,
                36500,
                true,
                true);
    }

    public static Duration[] parseSteps(String steps) {
        if (steps == null || steps.isBlank()) {
            return new Duration[0];
        }

        String[] parts = steps.split(",");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FsrsSchedulerConfig::parseSingle)
                .filter(d -> d != null)
                .toArray(Duration[]::new);
    }

    public static FsrsSchedulerConfig merge(FsrsParameters params, UserPreferences prefs) {
        FsrsSchedulerConfig defaults = defaults();

        double[] weights = (params != null) ? params.getWeights() : defaults.weights();

        double desiredRetention = (prefs != null && prefs.getDesiredRetention() != null)
                ? clampDesiredRetention(prefs.getDesiredRetention())
                : defaults.desiredRetention();

        Duration[] learningSteps = resolveSteps(prefs != null ? prefs.getLearningSteps() : null,
                defaults.learningSteps());
        Duration[] relearningSteps = resolveSteps(prefs != null ? prefs.getRelearningSteps() : null,
                defaults.relearningSteps());

        int maximumInterval = (prefs != null && prefs.getMaximumInterval() != null)
                ? prefs.getMaximumInterval()
                : defaults.maximumInterval();

        boolean enableFuzz = (prefs != null && prefs.getEnableFuzz() != null)
                ? prefs.getEnableFuzz()
                : defaults.enableFuzz();

        boolean enableShortTerm = (params != null) ? params.isEnableShortTerm() : defaults.enableShortTerm();

        return new FsrsSchedulerConfig(weights, desiredRetention, learningSteps, relearningSteps,
                maximumInterval, enableFuzz, enableShortTerm);
    }

    private static double clampDesiredRetention(double value) {
        if (value < 0.01 || value > 0.99) {
            log.warn("desiredRetention {} out of range [0.01, 0.99], clamping", value);
            return Math.max(0.01, Math.min(0.99, value));
        }
        return value;
    }

    private static Duration[] resolveSteps(String stepsStr, Duration[] fallback) {
        if (stepsStr == null || stepsStr.isBlank()) {
            return fallback;
        }
        Duration[] parsed = parseSteps(stepsStr);
        return parsed.length > 0 ? parsed : fallback;
    }

    private static Duration parseSingle(String s) {
        if (s.length() < 2) {
            log.warn("Invalid step format: '{}'", s);
            return null;
        }
        char unit = s.charAt(s.length() - 1);
        String numberPart = s.substring(0, s.length() - 1);
        long value;
        try {
            value = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            log.warn("Invalid step format: '{}'", s);
            return null;
        }
        if (value < 0) {
            log.warn("Negative step duration: '{}'", s);
            return null;
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> {
                log.warn("Unknown unit in step: '{}'", s);
                yield null;
            }
        };
    }
}
