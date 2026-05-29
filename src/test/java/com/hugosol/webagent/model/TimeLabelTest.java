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
    void today_lastNight_earlyHours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 0, 0), now)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 3, 0), now)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 5, 59), now)).isEqualTo("last night");
    }

    @Test
    void today_thisMorning() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 6, 0), now)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 9, 30), now)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 11, 59), now)).isEqualTo("this morning");
    }

    @Test
    void today_thisAfternoon() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 12, 0), now)).isEqualTo("this afternoon");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 13, 50), now)).isEqualTo("this afternoon");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 13, 0), now)).isEqualTo("this afternoon");
    }

    @Test
    void today_thisEvening() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 22, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 18, 0), now)).isEqualTo("this evening");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 20, 0), now)).isEqualTo("this evening");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 20, 50), now)).isEqualTo("this evening");
    }

    @Test
    void today_tonight() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 23, 30, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 22, 0), now)).isEqualTo("tonight");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 22, 10), now)).isEqualTo("tonight");
    }

    @Test
    void today_pastOneHourAbove_usesTimeSlot() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 9, 0), now)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 13, 50), now)).isEqualTo("this afternoon");
    }

    @Test
    void yesterday_lastNight_earlyHours() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 8, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 0, 0), now)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 3, 0), now)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 5, 59), now)).isEqualTo("last night");
    }

    @Test
    void yesterday_yesterdayMorning() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 6, 0), now)).isEqualTo("yesterday morning");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 9, 0), now)).isEqualTo("yesterday morning");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 11, 59), now)).isEqualTo("yesterday morning");
    }

    @Test
    void yesterday_yesterdayAfternoon() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 15, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 12, 0), now)).isEqualTo("yesterday afternoon");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 15, 30), now)).isEqualTo("yesterday afternoon");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 17, 59), now)).isEqualTo("yesterday afternoon");
    }

    @Test
    void yesterday_yesterdayEvening() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 8, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 18, 0), now)).isEqualTo("yesterday evening");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 20, 0), now)).isEqualTo("yesterday evening");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 21, 59), now)).isEqualTo("yesterday evening");
    }

    @Test
    void yesterday_lastNight_lateNight() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 8, 0, 0);
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 22, 0), now)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(LocalDateTime.of(2026, 5, 28, 23, 30), now)).isEqualTo("last night");
    }

    @Test
    void aFewDaysAgo_2to7Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(2), now)).isEqualTo("a few days ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(5), now)).isEqualTo("a few days ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(7), now)).isEqualTo("a few days ago");
    }

    @Test
    void aboutAWeekAgo_8to14Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(8), now)).isEqualTo("about a week ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(10), now)).isEqualTo("about a week ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(14), now)).isEqualTo("about a week ago");
    }

    @Test
    void aFewWeeksAgo_15to30Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(15), now)).isEqualTo("a few weeks ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(20), now)).isEqualTo("a few weeks ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(30), now)).isEqualTo("a few weeks ago");
    }

    @Test
    void aboutAMonthAgo_31to60Days() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(31), now)).isEqualTo("about a month ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(45), now)).isEqualTo("about a month ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(60), now)).isEqualTo("about a month ago");
    }

    @Test
    void aWhileAgo_over60Days_fallback() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(now.minusDays(61), now)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(200), now)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(365), now)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minusDays(1000), now)).isEqualTo("a while ago");
    }

    @Test
    void nullEventTime_returnsEmptyString() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0, 0);
        assertThat(TimeLabel.computeLabel(null, now)).isEmpty();
    }
}

