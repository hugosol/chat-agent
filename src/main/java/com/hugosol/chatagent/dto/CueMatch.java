package com.hugosol.chatagent.dto;

import java.io.Serializable;
import java.time.Instant;

public record CueMatch(

        String cueId,
        String topic,
        String summary,
        double score,
        Instant createdAt) implements Serializable {
}
