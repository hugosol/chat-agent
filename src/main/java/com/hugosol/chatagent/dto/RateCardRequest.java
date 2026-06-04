package com.hugosol.chatagent.dto;

public record RateCardRequest(String cardId, String rating, String deckId, String mode, int limit) {
}
