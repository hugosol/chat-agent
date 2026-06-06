package com.hugosol.chatagent.service;

import com.hugosol.chatagent.dto.ForgetDeckResult;
import com.hugosol.chatagent.flashcard.AleaPrng;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final CardRepository cardRepository;
    private final UserPreferencesService preferencesService;
    private final ReviewLogRepository reviewLogRepository;
    private final FsrsConfigService fsrsConfigService;
    private final CacheManager cacheManager;
    private final ExecutorService optimizerExecutor;

    public ReviewService(CardRepository cardRepository, UserPreferencesService preferencesService,
                         ReviewLogRepository reviewLogRepository, FsrsConfigService fsrsConfigService,
                         CacheManager cacheManager,
                         @Qualifier("optimizerExecutor") ExecutorService optimizerExecutor) {
        this.cardRepository = cardRepository;
        this.preferencesService = preferencesService;
        this.reviewLogRepository = reviewLogRepository;
        this.fsrsConfigService = fsrsConfigService;
        this.cacheManager = cacheManager;
        this.optimizerExecutor = optimizerExecutor;
    }

    @Transactional
    public Card rateCard(String cardId, Rating rating, String mode, Instant now, String userId, String deckId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        boolean isFirstReview = (card.getFirstReviewDate() == null);

        Instant originalDue = card.getDue();
        Instant originalLastReview = card.getLastReview();

        FsrsSchedulerConfig config = fsrsConfigService.getConfig(userId);
        FsrsScheduler scheduler = new FsrsScheduler(config);

        CardState inputState = buildCardState(card, scheduler, now);

        CardState result = scheduler.repeat(inputState, rating, now,
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

        return card;
    }

    public Map<Rating, CardState> previewCard(Card card, Instant now) {
        FsrsSchedulerConfig config = fsrsConfigService.getConfig(card.getUserId());
        FsrsScheduler scheduler = new FsrsScheduler(config);
        CardState cardState = buildCardState(card, scheduler, now);
        return scheduler.preview(cardState, now);
    }

    private CardState buildCardState(Card card, FsrsScheduler scheduler, Instant now) {
        if (card.getCardState() == 0) {
            return scheduler.enchantCard(now);
        }
        return new CardState(
                card.getStability(), card.getDifficulty(), card.getCardState(),
                card.getStep(), card.getDue(), card.getReps(), card.getLapses(), card.getLastReview(),
                0.0, true);
    }

    @Transactional
    public void forgetCard(String cardId, String userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        reviewLogRepository.deleteByCardId(cardId);

        Instant now = Instant.now();
        CardState initState = FsrsScheduler.createInitState(now);

        card.setStability(initState.stability());
        card.setDifficulty(initState.difficulty());
        card.setCardState(initState.state());
        card.setDue(initState.due());
        card.setReps(initState.reps());
        card.setLapses(initState.lapses());
        card.setStep(initState.step());
        card.setLastReview(initState.lastReview());
        card.setFirstReviewDate(null);

        cardRepository.save(card);
    }

    @Transactional
    public ForgetDeckResult forgetDeck(String deckId, String userId) {
        List<String> cardIds = cardRepository.findByFilteredDeckIds(deckId, userId);
        if (cardIds.isEmpty()) {
            return new ForgetDeckResult(0, 0);
        }

        int totalReviewCount = 0;
        for (String cardId : cardIds) {
            totalReviewCount += reviewLogRepository.countByCardId(cardId);
        }

        reviewLogRepository.deleteByCardIdIn(cardIds);

        List<Card> cards = cardRepository.findAllById(cardIds);
        Instant now = Instant.now();

        for (Card card : cards) {
            CardState initState = FsrsScheduler.createInitState(now);

            card.setStability(initState.stability());
            card.setDifficulty(initState.difficulty());
            card.setCardState(initState.state());
            card.setDue(initState.due());
            card.setReps(initState.reps());
            card.setLapses(initState.lapses());
            card.setStep(initState.step());
            card.setLastReview(initState.lastReview());
            card.setFirstReviewDate(null);
        }

        cardRepository.saveAll(cards);

        return new ForgetDeckResult(cards.size(), totalReviewCount);
    }

    public void rescheduleAllCards(String userId) {
        CompletableFuture.runAsync(() -> {
            List<ReviewLog> allLogs = reviewLogRepository.findByUserIdOrderByReviewedAtAsc(userId);
            if (allLogs.isEmpty()) {
                return;
            }

            Map<String, List<ReviewLog>> logsByCard = allLogs.stream()
                    .collect(Collectors.groupingBy(ReviewLog::getCardId));

            List<String> cardIds = new ArrayList<>(logsByCard.keySet());
            List<Card> cards = cardRepository.findAllById(cardIds);

            List<Card> updatedCards = new ArrayList<>();
            Instant now = Instant.now();

            for (Card card : cards) {
                List<ReviewLog> logs = logsByCard.get(card.getId());
                if (logs == null || logs.isEmpty()) {
                    continue;
                }
                FsrsSchedulerConfig config = fsrsConfigService.getConfig(userId);
                FsrsScheduler scheduler = new FsrsScheduler(config);
                CardState result = scheduler.reschedule(logs, now);

                card.setStability(result.stability());
                card.setDifficulty(result.difficulty());
                card.setCardState(result.state());
                card.setDue(result.due());
                card.setReps(result.reps());
                card.setLapses(result.lapses());
                card.setStep(result.step());
                card.setLastReview(result.lastReview());

                updatedCards.add(card);
            }

            if (!updatedCards.isEmpty()) {
                cardRepository.saveAll(updatedCards);
            }

            cacheManager.getCache("fsrsConfig").evict(userId);
        }, optimizerExecutor);
    }

    public ReviewStats computeReviewStats(String deckId, String mode, String userId) {
        return computeStats(deckId, mode, userId);
    }

    public Optional<Card> getNextCard(String deckId, String mode, String userId) {
        Instant now = Instant.now();
        UserPreferences prefs = preferencesService.get(userId);
        boolean shuffle = prefs.getShuffleDueCards() != null && prefs.getShuffleDueCards();

        switch (mode) {
            case "STANDARD" -> {
                Optional<Card> dueCard = shuffle
                        ? cardRepository.findRandomDueCardByDeckId(deckId, now, userId)
                        : cardRepository.findFirstDueCardByDeckId(deckId, now, userId);
                if (dueCard.isPresent()) {
                    return dueCard;
                }
                if (isNewCardLimitExceeded(deckId, userId, prefs)) {
                    return Optional.empty();
                }
                return shuffle
                        ? cardRepository.findRandomNewCardByDeckId(deckId, userId)
                        : cardRepository.findFirstNewCardByDeckId(deckId, userId);
            }
            case "REVIEW_ONLY" -> {
                return shuffle
                        ? cardRepository.findRandomDueCardByDeckId(deckId, now, userId)
                        : cardRepository.findFirstDueCardByDeckId(deckId, now, userId);
            }
            case "NEW_ONLY" -> {
                if (isNewCardLimitExceeded(deckId, userId, prefs)) {
                    return Optional.empty();
                }
                return shuffle
                        ? cardRepository.findRandomNewCardByDeckId(deckId, userId)
                        : cardRepository.findFirstNewCardByDeckId(deckId, userId);
            }
            case "CRAM" -> {
                return cardRepository.findRandomCardByDeckId(deckId, userId);
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private boolean isNewCardLimitExceeded(String deckId, String userId, UserPreferences prefs) {
        Instant todayStart = computeTodayStart(prefs);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart, userId);
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

    private ReviewStats computeStats(String deckId, String mode, String userId) {
        if (deckId == null) {
            return new ReviewStats(0, 0, 0, 20, null);
        }
        UserPreferences prefs = preferencesService.get(userId);
        Instant todayStart = computeTodayStart(prefs);
        Instant now = Instant.now();
        long reviewedToday = cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(deckId, todayStart, userId);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart, userId);
        long remaining = computeRemaining(deckId, mode, userId, prefs, learnedToday, now);
        Instant nextDueAt = cardRepository.findFirstDueByTagsIdAndDueAfter(deckId, now, userId);
        long displayedLearnedToday = "CRAM".equals(mode) ? -1 : learnedToday;
        return new ReviewStats(reviewedToday, remaining, displayedLearnedToday, prefs.getNewCardDailyLimit(), nextDueAt);
    }

    private long computeRemaining(String deckId, String mode, String userId, UserPreferences prefs, long learnedToday, Instant now) {
        return switch (mode) {
            case "STANDARD" -> {
                long dueCount = cardRepository.countDueCardsByTagsId(deckId, now, userId);
                long actualNewCards = cardRepository.countByTagsIdAndCardState(deckId, 0, userId);
                long newQuota = Math.max(0, prefs.getNewCardDailyLimit() - learnedToday);
                yield dueCount + Math.min(newQuota, actualNewCards);
            }
            case "REVIEW_ONLY" -> cardRepository.countDueCardsByTagsId(deckId, now, userId);
            case "NEW_ONLY" -> {
                long actualNewCards = cardRepository.countByTagsIdAndCardState(deckId, 0, userId);
                long newQuota = Math.max(0, prefs.getNewCardDailyLimit() - learnedToday);
                yield Math.min(newQuota, actualNewCards);
            }
            case "CRAM" -> -1;
            default -> 0;
        };
    }
}
