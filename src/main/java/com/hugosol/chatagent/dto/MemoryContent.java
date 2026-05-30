package com.hugosol.chatagent.dto;

import java.util.List;

public record MemoryContent(
        String lastConversationTimeLabel,
        String learningProfile,
        List<CueMatch> cueMatches) {

    public boolean isEmpty() {
        return (lastConversationTimeLabel == null || lastConversationTimeLabel.isEmpty())
                && (learningProfile == null || learningProfile.isEmpty())
                && (cueMatches == null || cueMatches.isEmpty());
    }
}
