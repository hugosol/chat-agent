package com.hugosol.webagent.dto;

public record CueMatch(
        String cueId,
        String topic,
        String summary,
        double score) {
}
