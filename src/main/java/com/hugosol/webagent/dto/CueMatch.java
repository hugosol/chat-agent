package com.hugosol.webagent.dto;

import java.time.LocalDateTime;

public record CueMatch(
        String cueId,
        String topic,
        String summary,
        double score,
        LocalDateTime createdAt) {
}
