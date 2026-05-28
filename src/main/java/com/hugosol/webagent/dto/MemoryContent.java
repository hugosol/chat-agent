package com.hugosol.webagent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MemoryContent(
        String topicSummary,
        String learningProfile,
        String memoryCuesText,
        LocalDateTime topicCreatedAt,
        List<LocalDateTime> cueCreatedAts) {

    public MemoryContent {
        if (cueCreatedAts == null) {
            cueCreatedAts = List.of();
        }
    }

    public MemoryContent(String topicSummary, String learningProfile, String memoryCuesText) {
        this(topicSummary, learningProfile, memoryCuesText, null, List.of());
    }

    public boolean isEmpty() {
        return (topicSummary == null || topicSummary.isEmpty())
                && (learningProfile == null || learningProfile.isEmpty())
                && (memoryCuesText == null || memoryCuesText.isEmpty());
    }
}
