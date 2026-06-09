package com.hugosol.chatagent.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public enum TimeLabel {
    JUST_NOW("just now"),
    A_FEW_MINUTES_AGO("a few minutes ago"),
    LAST_NIGHT("last night"),
    THIS_MORNING("this morning"),
    THIS_AFTERNOON("this afternoon"),
    THIS_EVENING("this evening"),
    TONIGHT("tonight"),
    YESTERDAY_MORNING("yesterday morning"),
    YESTERDAY_AFTERNOON("yesterday afternoon"),
    YESTERDAY_EVENING("yesterday evening"),
    A_FEW_DAYS_AGO("a few days ago"),
    ABOUT_A_WEEK_AGO("about a week ago"),
    A_FEW_WEEKS_AGO("a few weeks ago"),
    ABOUT_A_MONTH_AGO("about a month ago"),
    A_WHILE_AGO("a while ago");

    private final String label;

    TimeLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static String computeLabel(Instant eventTime, Instant referenceTime, ZoneId zoneId) {
        if (eventTime == null) return "";

        LocalDateTime eventLocal = eventTime.atZone(zoneId).toLocalDateTime();
        LocalDateTime referenceLocal = referenceTime.atZone(zoneId).toLocalDateTime();

        Duration elapsed = Duration.between(eventLocal, referenceLocal);
        if (elapsed.isNegative()) return "";

        if (elapsed.compareTo(Duration.ofMinutes(5)) <= 0) {
            return JUST_NOW.label;
        }
        if (elapsed.compareTo(Duration.ofHours(1)) <= 0) {
            return A_FEW_MINUTES_AGO.label;
        }

        LocalDate eventDate = eventLocal.toLocalDate();
        LocalDate refDate = referenceLocal.toLocalDate();
        long daysBetween = ChronoUnit.DAYS.between(eventDate, refDate);

        if (daysBetween == 0) {
            return timeSlotLabel(eventLocal.toLocalTime(), "this", true);
        }
        if (daysBetween == 1) {
            return timeSlotLabel(eventLocal.toLocalTime(), "yesterday", false);
        }

        long totalDays = elapsed.toDays();
        if (totalDays <= 7) return A_FEW_DAYS_AGO.label;
        if (totalDays <= 14) return ABOUT_A_WEEK_AGO.label;
        if (totalDays <= 30) return A_FEW_WEEKS_AGO.label;
        if (totalDays <= 60) return ABOUT_A_MONTH_AGO.label;
        return A_WHILE_AGO.label;
    }

    private static String timeSlotLabel(LocalTime time, String prefix, boolean isToday) {
        int hour = time.getHour();
        if (hour < 6) return LAST_NIGHT.label;
        if (hour < 12) return isToday ? THIS_MORNING.label : YESTERDAY_MORNING.label;
        if (hour < 18) return isToday ? THIS_AFTERNOON.label : YESTERDAY_AFTERNOON.label;
        if (hour < 22) return isToday ? THIS_EVENING.label : YESTERDAY_EVENING.label;
        return isToday ? TONIGHT.label : LAST_NIGHT.label;
    }
}
