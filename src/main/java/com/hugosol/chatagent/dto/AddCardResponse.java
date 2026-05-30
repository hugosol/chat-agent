package com.hugosol.chatagent.dto;

import java.util.List;

public record AddCardResponse(String id, String front, String back, List<TagResponse> tags, String due) {
}
