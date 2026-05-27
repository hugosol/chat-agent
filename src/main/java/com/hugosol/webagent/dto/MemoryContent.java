package com.hugosol.webagent.dto;

public record MemoryContent(
        String topicSummary,
        String learningProfile,
        String memoryCuesText) {

    public boolean isEmpty() {
        return (topicSummary == null || topicSummary.isEmpty())
                && (learningProfile == null || learningProfile.isEmpty())
                && (memoryCuesText == null || memoryCuesText.isEmpty());
    }
}
