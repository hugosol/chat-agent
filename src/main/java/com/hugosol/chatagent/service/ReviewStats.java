package com.hugosol.chatagent.service;

public record ReviewStats(long reviewedToday, long remaining, long learnedToday, int dailyLimit, java.time.Instant nextDueAt) {
}
