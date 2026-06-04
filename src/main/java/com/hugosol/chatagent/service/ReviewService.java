package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.AleaPrng;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
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
    private final ReviewLogRepository reviewLogRepository;

    public ReviewService(CardRepository cardRepository, UserPreferencesService preferencesService,
                         ReviewLogRepository reviewLogRepository) {
        this.cardRepository = cardRepository;
        this.preferencesService = preferencesService;
        this.reviewLogRepository = reviewLogRepository;
    }

    @Transactional
    public RateCardResult rateCard(String cardId, Rating rating, Instant now, String userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        boolean isFirstReview = (card.getFirstReviewDate() == null);

        Instant originalDue = card.getDue();
        Instant originalLastReview = card.getLastReview();

        CardState inputState;
        if (card.getCardState() == 0) {
            inputState = FsrsScheduler.initNewCard(now);
        } else {
            inputState = new CardState(
                    card.getStability(), card.getDifficulty(), card.getCardState(),
                    card.getStep(), card.getDue(), card.getReps(), card.getLapses(), card.getLastReview(),
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
        card.setStep(result.step());
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

        ReviewLog log = new ReviewLog();
        log.setUserId(userId);
        log.setCardId(cardId);
        log.setRating(rating);
        log.setStateBefore(inputState.state());
        log.setStateAfter(result.state());
        log.setStabilityBefore(inputState.stability());
        log.setStabilityAfter(result.stability());
        log.setDifficultyBefore(inputState.difficulty());
        log.setDifficultyAfter(result.difficulty());
        log.setStepBefore(inputState.step());
        log.setScheduledDays(originalLastReview != null
                ? Duration.between(originalLastReview, originalDue).getSeconds() / 86400.0
                : 0);
        log.setElapsedDays(originalLastReview != null
                ? Duration.between(originalLastReview, now).getSeconds() / 86400.0
                : 0);
        log.setReviewedAt(now);
        log.setFirstReview(isFirstReview);
        log.setDeckId(deckId);
        reviewLogRepository.save(log);

        ReviewStats stats = computeStats(deckId, userId);

        return new RateCardResult(card, stats);
    }

    public Optional<Card> getNextCard(String deckId, String mode, String userId) {
        Instant now = Instant.now();
        UserPreferences prefs = preferencesService.get(userId);

        switch (mode) {
            case "STANDARD" -> {
                Optional<Card> dueCard = cardRepository.findFirstDueCardByDeckId(deckId, now);
                if (dueCard.isPresent()) {
                    return dueCard;
                }
                if (isNewCardLimitExceeded(deckId, prefs)) {
                    return Optional.empty();
                }
                return cardRepository.findFirstNewCardByDeckId(deckId);
            }
            case "REVIEW_ONLY" -> {
                return cardRepository.findFirstDueCardByDeckId(deckId, now);
            }
            case "NEW_ONLY" -> {
                if (isNewCardLimitExceeded(deckId, prefs)) {
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

    private boolean isNewCardLimitExceeded(String deckId, UserPreferences prefs) {
        Instant todayStart = computeTodayStart(prefs);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart);
        return learnedToday >= prefs.getNewCardDailyLimit();
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

    private ReviewStats computeStats(String deckId, String userId) {
        if (deckId == null) {
            return new ReviewStats(0, 0, 0, 20, null);
        }
        UserPreferences prefs = preferencesService.get(userId);
        Instant todayStart = computeTodayStart(prefs);
        Instant now = Instant.now();
        long reviewedToday = cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(deckId, todayStart);
        long remaining = cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual(deckId, 0, now);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart);
        Instant nextDueAt = cardRepository.findFirstDueByTagsIdAndDueAfter(deckId, now);
        return new ReviewStats(reviewedToday, remaining, learnedToday, prefs.getNewCardDailyLimit(), nextDueAt);
    }
}
