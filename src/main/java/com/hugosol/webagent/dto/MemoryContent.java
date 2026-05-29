package com.hugosol.webagent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MemoryContent(
        String topicSummary,
        String learningProfile,
        List<CueMatch> cueMatches,
        LocalDateTime topicCreatedAt) {

    public MemoryContent(String topicSummary, String learningProfile, List<CueMatch> cueMatches) {
        this(topicSummary, learningProfile, cueMatches, null);
    }

    public boolean isEmpty() {
        return (topicSummary == null || topicSummary.isEmpty())
                && (learningProfile == null || learningProfile.isEmpty())
                && (cueMatches == null || cueMatches.isEmpty());
    }
}
