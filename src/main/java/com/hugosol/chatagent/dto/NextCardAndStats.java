package com.hugosol.chatagent.dto;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.service.ReviewStats;

import java.util.Optional;

public record NextCardAndStats(Optional<Card> card, ReviewStats stats) {}
