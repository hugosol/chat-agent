package com.hugosol.chatagent.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.UserPreferences;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class FsrsSchedulerConfigTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void defaults_returnsFsrs6StandardWeights() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.defaults();

        double[] w = config.weights();
        assertThat(w).hasSize(21);
        assertThat(w[0]).isCloseTo(0.212, within(TOLERANCE));
        assertThat(w[1]).isCloseTo(1.2931, within(TOLERANCE));
        assertThat(w[2]).isCloseTo(2.3065, within(TOLERANCE));
        assertThat(w[3]).isCloseTo(8.2956, within(TOLERANCE));
        assertThat(w[4]).isCloseTo(6.4133, within(TOLERANCE));
        assertThat(w[5]).isCloseTo(0.8334, within(TOLERANCE));
        assertThat(w[6]).isCloseTo(3.0194, within(TOLERANCE));
        assertThat(w[7]).isCloseTo(0.001, within(TOLERANCE));
        assertThat(w[8]).isCloseTo(1.8722, within(TOLERANCE));
        assertThat(w[9]).isCloseTo(0.1666, within(TOLERANCE));
        assertThat(w[10]).isCloseTo(0.796, within(TOLERANCE));
        assertThat(w[11]).isCloseTo(1.4835, within(TOLERANCE));
        assertThat(w[12]).isCloseTo(0.0614, within(TOLERANCE));
        assertThat(w[13]).isCloseTo(0.2629, within(TOLERANCE));
        assertThat(w[14]).isCloseTo(1.6483, within(TOLERANCE));
        assertThat(w[15]).isCloseTo(0.6014, within(TOLERANCE));
        assertThat(w[16]).isCloseTo(1.8729, within(TOLERANCE));
        assertThat(w[17]).isCloseTo(0.5425, within(TOLERANCE));
        assertThat(w[18]).isCloseTo(0.0912, within(TOLERANCE));
        assertThat(w[19]).isCloseTo(0.0658, within(TOLERANCE));
        assertThat(w[20]).isCloseTo(0.1542, within(TOLERANCE));
    }

    @Test
    void defaults_returnsCorrectParameters() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.defaults();

        assertThat(config.desiredRetention()).isCloseTo(0.9, within(TOLERANCE));
        assertThat(config.maximumInterval()).isEqualTo(36500);
        assertThat(config.enableFuzz()).isTrue();
        assertThat(config.enableShortTerm()).isTrue();
    }

    @Test
    void defaults_learningStepsAre1mAnd10m() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.defaults();

        assertThat(config.learningSteps()).hasSize(2);
        assertThat(config.learningSteps()[0]).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.learningSteps()[1]).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void defaults_relearningStepsAre10m() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.defaults();

        assertThat(config.relearningSteps()).hasSize(1);
        assertThat(config.relearningSteps()[0]).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void parseSteps_1m10m_returnsTwoDurations() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("1m,10m");

        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(Duration.ofMinutes(1));
        assertThat(result[1]).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void parseSteps_30s_returnsOneDuration() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("30s");

        assertThat(result).hasSize(1);
        assertThat(result[0]).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void parseSteps_mixedUnits_parsesCorrectly() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("1h,30m,45s,2d");

        assertThat(result).hasSize(4);
        assertThat(result[0]).isEqualTo(Duration.ofHours(1));
        assertThat(result[1]).isEqualTo(Duration.ofMinutes(30));
        assertThat(result[2]).isEqualTo(Duration.ofSeconds(45));
        assertThat(result[3]).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void parseSteps_emptyString_returnsEmpty() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("");

        assertThat(result).isEmpty();
    }

    @Test
    void parseSteps_null_returnsEmpty() {
        Duration[] result = FsrsSchedulerConfig.parseSteps(null);

        assertThat(result).isEmpty();
    }

    @Test
    void parseSteps_invalidFormat_returnsEmpty() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("abc");

        assertThat(result).isEmpty();
    }

    @Test
    void parseSteps_partiallyInvalid_parsesValidPortions() {
        Duration[] result = FsrsSchedulerConfig.parseSteps("1m,xyz,30s");

        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(Duration.ofMinutes(1));
        assertThat(result[1]).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void parseSteps_whitespace_trimmed() {
        Duration[] result = FsrsSchedulerConfig.parseSteps(" 1m , 10m ");

        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(Duration.ofMinutes(1));
        assertThat(result[1]).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void merge_allNulls_returnsDefaults() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, null);
        FsrsSchedulerConfig defaults = FsrsSchedulerConfig.defaults();

        assertThat(config.desiredRetention()).isEqualTo(defaults.desiredRetention());
        assertThat(config.maximumInterval()).isEqualTo(defaults.maximumInterval());
        assertThat(config.enableFuzz()).isEqualTo(defaults.enableFuzz());
        assertThat(config.enableShortTerm()).isEqualTo(defaults.enableShortTerm());
        assertThat(config.learningSteps()).containsExactly(defaults.learningSteps());
        assertThat(config.relearningSteps()).containsExactly(defaults.relearningSteps());
    }

    @Test
    void merge_nullParams_weightsUseDefaults() {
        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, new UserPreferences());
        double[] w = config.weights();
        assertThat(w[0]).isCloseTo(0.212, within(TOLERANCE));
        assertThat(w[20]).isCloseTo(0.1542, within(TOLERANCE));
    }

    @Test
    void merge_partialOverrides_mergesCorrectly() {
        UserPreferences prefs = new UserPreferences();
        prefs.setDesiredRetention(0.85);
        prefs.setMaximumInterval(180);
        prefs.setEnableFuzz(false);

        FsrsParameters params = FsrsParameters.defaults("u1");

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, prefs);

        assertThat(config.desiredRetention()).isCloseTo(0.85, within(TOLERANCE));
        assertThat(config.maximumInterval()).isEqualTo(180);
        assertThat(config.enableFuzz()).isFalse();
        assertThat(config.enableShortTerm()).isTrue();
        assertThat(config.learningSteps()).hasSize(2);
    }

    @Test
    void merge_desiredRetentionOutOfBounds_clamped() {
        UserPreferences prefs = new UserPreferences();
        prefs.setDesiredRetention(0.999);

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, prefs);
        assertThat(config.desiredRetention()).isCloseTo(0.99, within(TOLERANCE));

        prefs.setDesiredRetention(0.001);
        config = FsrsSchedulerConfig.merge(null, prefs);
        assertThat(config.desiredRetention()).isCloseTo(0.01, within(TOLERANCE));
    }

    @Test
    void merge_customLearningSteps_parsedAndUsed() {
        UserPreferences prefs = new UserPreferences();
        prefs.setLearningSteps("30s,2m");

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, prefs);

        assertThat(config.learningSteps()).hasSize(2);
        assertThat(config.learningSteps()[0]).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.learningSteps()[1]).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void merge_invalidLearningSteps_fallsBackToDefaults() {
        UserPreferences prefs = new UserPreferences();
        prefs.setLearningSteps("abc");

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, prefs);
        assertThat(config.learningSteps()).hasSize(2);
        assertThat(config.learningSteps()[0]).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void merge_emptyLearningSteps_fallsBackToDefaults() {
        UserPreferences prefs = new UserPreferences();
        prefs.setLearningSteps("");

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(null, prefs);
        assertThat(config.learningSteps()).hasSize(2);
    }

    @Test
    void merge_paramsWeights_usedWhenPresent() {
        FsrsParameters params = FsrsParameters.defaults("u1");
        double[] customW = params.getWeights();
        customW[0] = 0.5;
        params.setW0(0.5);

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, null);
        assertThat(config.weights()[0]).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void merge_enableShortTerm_fromParams() {
        FsrsParameters params = FsrsParameters.defaults("u1");
        params.setEnableShortTerm(false);

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, null);
        assertThat(config.enableShortTerm()).isFalse();
    }

    @Test
    void merge_nullPrefs_usesAllDefaults() {
        FsrsParameters params = FsrsParameters.defaults("u1");

        FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, null);
        FsrsSchedulerConfig defaults = FsrsSchedulerConfig.defaults();

        assertThat(config.desiredRetention()).isEqualTo(defaults.desiredRetention());
        assertThat(config.maximumInterval()).isEqualTo(defaults.maximumInterval());
        assertThat(config.enableFuzz()).isEqualTo(defaults.enableFuzz());
    }
}
