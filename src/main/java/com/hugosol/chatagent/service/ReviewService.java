package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.AleaPrng;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class ReviewService {

    private final CardRepository cardRepository;
    private final UserPreferencesService preferencesService;

    public ReviewService(CardRepository cardRepository, UserPreferencesService preferencesService) {
        this.cardRepository = cardRepository;
        this.preferencesService = preferencesService;
    }

    @Transactional
    public RateCardResult rateCard(String cardId, Rating rating, Instant now, String userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        boolean isFirstReview = (card.getFirstReviewDate() == null);

        CardState inputState;
        if (card.getCardState() == 0) {
            inputState = FsrsScheduler.initNewCard(now);
        } else {
            inputState = new CardState(
                    card.getStability(), card.getDifficulty(), card.getCardState(),
                    0, card.getDue(), card.getReps(), card.getLapses(), card.getLastReview(),
                    0.0, true);
        }

        CardState result = FsrsScheduler.repeat(inputState, rating, now,
                new AleaPrng(now.toEpochMilli())::next);

        card.setStability(result.stability());
        card.setDifficulty(result.difficulty());
        card.setCardState(result.state());
        card.setDue(result.due());
        card.setReps(result.reps());
        card.setLapses(result.lapses());
        card.setLastReview(result.lastReview());
        if (isFirstReview) {
            card.setFirstReviewDate(now);
        }

        card = cardRepository.save(card);

        String deckId = card.getTags().stream()
                .filter(t -> "deck".equals(t.getType()))
                .findFirst()
                .map(Tag::getId)
                .orElse(null);

        ReviewStats stats = computeStats(deckId, now);

        return new RateCardResult(card, stats);
    }

    public Optional<Card> getNextCard(String deckId, String mode, int limit, String userId) {
        Instant now = Instant.now();
        UserPreferences prefs = preferencesService.get(userId);

        switch (mode) {
            case "STANDARD" -> {
                Optional<Card> dueCard = cardRepository.findFirstDueCardByDeckId(deckId, now);
                if (dueCard.isPresent()) {
                    return dueCard;
                }
                if (isNewCardLimitExceeded(deckId, prefs, limit)) {
                    return Optional.empty();
                }
                return cardRepository.findFirstNewCardByDeckId(deckId);
            }
            case "REVIEW_ONLY" -> {
                return cardRepository.findFirstDueCardByDeckId(deckId, now);
            }
            case "NEW_ONLY" -> {
                if (isNewCardLimitExceeded(deckId, prefs, limit)) {
                    return Optional.empty();
                }
                return cardRepository.findFirstNewCardByDeckId(deckId);
            }
            case "CRAM" -> {
                return cardRepository.findRandomCardByDeckId(deckId);
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private boolean isNewCardLimitExceeded(String deckId, UserPreferences prefs, int limit) {
        Instant todayStart = computeTodayStart(prefs);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart);
        int effectiveLimit = limit > 0 ? limit : prefs.getNewCardDailyLimit();
        return learnedToday >= effectiveLimit;
    }

    private Instant computeTodayStart(UserPreferences prefs) {
        String timezone = prefs.getTimezone() != null ? prefs.getTimezone() : ZoneId.systemDefault().getId();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }
        ZonedDateTime nowInZone = ZonedDateTime.now(zoneId);
        LocalDate today = nowInZone.toLocalDate();
        LocalDateTime todayStart = today.atStartOfDay().plusHours(prefs.getDayStartHour());
        return todayStart.atZone(zoneId).toInstant();
    }

    private ReviewStats computeStats(String deckId, Instant now) {
        if (deckId == null) {
            return new ReviewStats(0, 0, 0, 20);
        }
        long reviewedToday = cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(deckId, now);
        long remaining = cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual(deckId, 0, now);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, now);
        return new ReviewStats(reviewedToday, remaining, learnedToday, 20);
    }
}
