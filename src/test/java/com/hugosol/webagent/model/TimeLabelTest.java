package com.hugosol.webagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeLabelTest {

    @Test
    void justNow_under5Minutes() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusSeconds(10), now)).isEqualTo("just now");
        assertThat(TimeLabel.computeLabel(now.minusMinutes(4), now)).isEqualTo("just now");
        assertThat(TimeLabel.computeLabel(now, now)).isEqualTo("just now");
    }

    @Test
    void justNow_boundaryAt5Minutes() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusMinutes(5), now)).isEqualTo("just now");
    }

    @Test
    void aFewMinutesAgo_justPast5Minutes() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusMinutes(5).minusSeconds(1), now))
                .isEqualTo("a few minutes ago");
    }

    @Test
    void aFewMinutesAgo_upTo1Hour() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusMinutes(30), now)).isEqualTo("a few minutes ago");
        assertThat(TimeLabel.computeLabel(now.minusHours(1), now)).isEqualTo("a few minutes ago");
    }

    @Test
    void earlierToday_boundaryCrosses1Hour() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusHours(1).minusSeconds(1), now))
                .isEqualTo("earlier today");
    }

    @Test
    void earlierToday_upTo12Hours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusHours(6), now)).isEqualTo("earlier today");
        assertThat(TimeLabel.computeLabel(now.minusHours(12), now)).isEqualTo("earlier today");
    }

    @Test
    void yesterday_boundaryCrosses12Hours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusHours(12).minusSeconds(1), now))
                .isEqualTo("yesterday");
    }

    @Test
    void yesterday_upTo48Hours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusHours(24), now)).isEqualTo("yesterday");
        assertThat(TimeLabel.computeLabel(now.minusHours(48), now)).isEqualTo("yesterday");
    }

    @Test
    void aFewDaysAgo_boundaryCrosses48Hours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusHours(48).minusSeconds(1), now))
                .isEqualTo("a few days ago");
    }

    @Test
    void aFewDaysAgo_upTo7Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(5), now)).isEqualTo("a few days ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(7), now)).isEqualTo("a few days ago");
    }

    @Test
    void aboutAWeekAgo_boundaryCrosses7Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(7).minusSeconds(1), now))
                .isEqualTo("about a week ago");
    }

    @Test
    void aboutAWeekAgo_upTo14Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(10), now)).isEqualTo("about a week ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(14), now)).isEqualTo("about a week ago");
    }

    @Test
    void aFewWeeksAgo_boundaryCrosses14Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(14).minusSeconds(1), now))
                .isEqualTo("a few weeks ago");
    }

    @Test
    void aFewWeeksAgo_upTo30Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(20), now)).isEqualTo("a few weeks ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(30), now)).isEqualTo("a few weeks ago");
    }

    @Test
    void aboutAMonthAgo_boundaryCrosses30Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(30).minusSeconds(1), now))
                .isEqualTo("about a month ago");
    }

    @Test
    void aboutAMonthAgo_upTo60Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(45), now)).isEqualTo("about a month ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(60), now)).isEqualTo("about a month ago");
    }

    @Test
    void aWhileAgo_boundaryCrosses60Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(60).minusSeconds(1), now))
                .isEqualTo("a while ago");
    }

    @Test
    void aWhileAgo_upTo365Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(200), now)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(365), now)).isEqualTo("a while ago");
    }

    @Test
    void aWhileAgo_past365Days_fallback() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(366), now)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(1000), now)).isEqualTo("a while ago");
    }
}
