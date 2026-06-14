package com.hugosol.chatagent.dto;

import java.util.List;

public record CheckCardResponse(List<ConflictInfo> conflicts) {
    public record ConflictInfo(String tagId, String tagName) {}
}
