package com.hugosol.chatagent.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeLabelTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    @Test
    void justNow_under5Minutes() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minusSeconds(10), now, ZONE)).isEqualTo("just now");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofMinutes(4)), now, ZONE)).isEqualTo("just now");
        assertThat(TimeLabel.computeLabel(now, now, ZONE)).isEqualTo("just now");
    }

    @Test
    void justNow_boundaryAt5Minutes() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofMinutes(5)), now, ZONE)).isEqualTo("just now");
    }

    @Test
    void aFewMinutesAgo_justPast5Minutes() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minusSeconds(5 * 60 + 1), now, ZONE))
                .isEqualTo("a few minutes ago");
    }

    @Test
    void aFewMinutesAgo_upTo1Hour() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofMinutes(30)), now, ZONE)).isEqualTo("a few minutes ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofHours(1)), now, ZONE)).isEqualTo("a few minutes ago");
    }

    @Test
    void today_lastNight_earlyHours() {
        Instant now = instant(2026, 5, 28, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 0, 0), now, ZONE)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 3, 0), now, ZONE)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 5, 59), now, ZONE)).isEqualTo("last night");
    }

    @Test
    void today_thisMorning() {
        Instant now = instant(2026, 5, 28, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 6, 0), now, ZONE)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 9, 30), now, ZONE)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 11, 59), now, ZONE)).isEqualTo("this morning");
    }

    @Test
    void today_thisAfternoon() {
        Instant now = instant(2026, 5, 28, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 12, 0), now, ZONE)).isEqualTo("this afternoon");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 13, 50), now, ZONE)).isEqualTo("this afternoon");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 13, 0), now, ZONE)).isEqualTo("this afternoon");
    }

    @Test
    void today_thisEvening() {
        Instant now = instant(2026, 5, 28, 22, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 18, 0), now, ZONE)).isEqualTo("this evening");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 20, 0), now, ZONE)).isEqualTo("this evening");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 20, 50), now, ZONE)).isEqualTo("this evening");
    }

    @Test
    void today_tonight() {
        Instant now = instant(2026, 5, 28, 23, 30);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 22, 0), now, ZONE)).isEqualTo("tonight");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 22, 10), now, ZONE)).isEqualTo("tonight");
    }

    @Test
    void today_pastOneHourAbove_usesTimeSlot() {
        Instant now = instant(2026, 5, 28, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 9, 0), now, ZONE)).isEqualTo("this morning");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 13, 50), now, ZONE)).isEqualTo("this afternoon");
    }

    @Test
    void yesterday_lastNight_earlyHours() {
        Instant now = instant(2026, 5, 29, 8, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 0, 0), now, ZONE)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 3, 0), now, ZONE)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 5, 59), now, ZONE)).isEqualTo("last night");
    }

    @Test
    void yesterday_yesterdayMorning() {
        Instant now = instant(2026, 5, 29, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 6, 0), now, ZONE)).isEqualTo("yesterday morning");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 9, 0), now, ZONE)).isEqualTo("yesterday morning");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 11, 59), now, ZONE)).isEqualTo("yesterday morning");
    }

    @Test
    void yesterday_yesterdayAfternoon() {
        Instant now = instant(2026, 5, 29, 15, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 12, 0), now, ZONE)).isEqualTo("yesterday afternoon");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 15, 30), now, ZONE)).isEqualTo("yesterday afternoon");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 17, 59), now, ZONE)).isEqualTo("yesterday afternoon");
    }

    @Test
    void yesterday_yesterdayEvening() {
        Instant now = instant(2026, 5, 29, 8, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 18, 0), now, ZONE)).isEqualTo("yesterday evening");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 20, 0), now, ZONE)).isEqualTo("yesterday evening");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 21, 59), now, ZONE)).isEqualTo("yesterday evening");
    }

    @Test
    void yesterday_lastNight_lateNight() {
        Instant now = instant(2026, 5, 29, 8, 0);
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 22, 0), now, ZONE)).isEqualTo("last night");
        assertThat(TimeLabel.computeLabel(instant(2026, 5, 28, 23, 30), now, ZONE)).isEqualTo("last night");
    }

    @Test
    void aFewDaysAgo_2to7Days() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(2)), now, ZONE)).isEqualTo("a few days ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(5)), now, ZONE)).isEqualTo("a few days ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(7)), now, ZONE)).isEqualTo("a few days ago");
    }

    @Test
    void aboutAWeekAgo_8to14Days() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(8)), now, ZONE)).isEqualTo("about a week ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(10)), now, ZONE)).isEqualTo("about a week ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(14)), now, ZONE)).isEqualTo("about a week ago");
    }

    @Test
    void aFewWeeksAgo_15to30Days() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(15)), now, ZONE)).isEqualTo("a few weeks ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(20)), now, ZONE)).isEqualTo("a few weeks ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(30)), now, ZONE)).isEqualTo("a few weeks ago");
    }

    @Test
    void aboutAMonthAgo_31to60Days() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(31)), now, ZONE)).isEqualTo("about a month ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(45)), now, ZONE)).isEqualTo("about a month ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(60)), now, ZONE)).isEqualTo("about a month ago");
    }

    @Test
    void aWhileAgo_over60Days_fallback() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(61)), now, ZONE)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(200)), now, ZONE)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(365)), now, ZONE)).isEqualTo("a while ago");
        assertThat(TimeLabel.computeLabel(now.minus(Duration.ofDays(1000)), now, ZONE)).isEqualTo("a while ago");
    }

    @Test
    void nullEventTime_returnsEmptyString() {
        Instant now = instant(2026, 5, 28, 12, 0);
        assertThat(TimeLabel.computeLabel(null, now, ZONE)).isEmpty();
    }
}
