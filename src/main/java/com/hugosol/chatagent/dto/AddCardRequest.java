package com.hugosol.chatagent.dto;

import java.util.List;

public record AddCardRequest(String front, String back, List<String> tags) {
}
