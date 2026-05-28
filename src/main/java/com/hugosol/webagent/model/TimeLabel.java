package com.hugosol.webagent.model;

import java.time.Duration;
import java.time.LocalDateTime;

public enum TimeLabel {
    JUST_NOW("just now", Duration.ofMinutes(5)),
    A_FEW_MINUTES_AGO("a few minutes ago", Duration.ofHours(1)),
    EARLIER_TODAY("earlier today", Duration.ofHours(12)),
    YESTERDAY("yesterday", Duration.ofHours(48)),
    A_FEW_DAYS_AGO("a few days ago", Duration.ofDays(7)),
    ABOUT_A_WEEK_AGO("about a week ago", Duration.ofDays(14)),
    A_FEW_WEEKS_AGO("a few weeks ago", Duration.ofDays(30)),
    ABOUT_A_MONTH_AGO("about a month ago", Duration.ofDays(60)),
    A_WHILE_AGO("a while ago", Duration.ofDays(365));

    private final String label;
    private final Duration maxDuration;

    TimeLabel(String label, Duration maxDuration) {
        this.label = label;
        this.maxDuration = maxDuration;
    }

    public String getLabel() {
        return label;
    }

    public static String computeLabel(LocalDateTime eventTime, LocalDateTime referenceTime) {
        Duration elapsed = Duration.between(eventTime, referenceTime);
        for (TimeLabel tl : values()) {
            if (elapsed.compareTo(tl.maxDuration) <= 0) {
                return tl.label;
            }
        }
        return A_WHILE_AGO.label;
    }
}
