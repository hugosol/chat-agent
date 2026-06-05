package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.ForgetDeckResult;
import com.hugosol.chatagent.dto.RateRequest;
import com.hugosol.chatagent.dto.TagResponse;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;
import com.hugosol.chatagent.service.ReviewService;
import com.hugosol.chatagent.service.UserPreferencesService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final UserPreferencesService preferencesService;
    private final TagRepository tagRepository;
    private final CardRepository cardRepository;

    public ReviewController(ReviewService reviewService, UserPreferencesService preferencesService,
                            TagRepository tagRepository, CardRepository cardRepository) {
        this.reviewService = reviewService;
        this.preferencesService = preferencesService;
        this.tagRepository = tagRepository;
        this.cardRepository = cardRepository;
    }

    @PostMapping("/review/next")
    public ResponseEntity<Map<String, Object>> processNextCard(@RequestBody RateRequest request) {
        String userId = getUserId();
        Rating rating = Rating.valueOf(request.rating().toUpperCase());
        reviewService.rateCard(request.cardId(), rating, request.mode(), Instant.now(), userId, request.deckId());
        var card = reviewService.getNextCard(request.deckId(), request.mode(), userId);
        var stats = reviewService.computeReviewStats(request.deckId(), request.mode(), userId);

        Map<String, Object> response = new HashMap<>();
        if (card.isPresent()) {
            response.put("card", cardToMap(card.get()));
            Map<Rating, CardState> preview = reviewService.previewCard(card.get(), Instant.now());
            response.put("preview", previewToMap(preview));
        } else {
            response.put("card", null);
        }
        response.put("stats", statsToMap(stats));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/review/decks")
    public ResponseEntity<List<Map<String, Object>>> getDecks() {
        String userId = getUserId();
        List<Tag> decks = tagRepository.findByUserIdAndType(userId, "deck");
        List<Map<String, Object>> result = decks.stream().map(deck -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", deck.getId());
            map.put("name", deck.getName());
            map.put("type", deck.getType());
            map.put("cardCount", cardRepository.countByTagsId(deck.getId()));
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/review/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam String deckId,
            @RequestParam(defaultValue = "") String mode) {
        String userId = getUserId();

        if (!mode.isEmpty()) {
            var stats = reviewService.computeReviewStats(deckId, mode, userId);
            return ResponseEntity.ok(statsToMap(stats));
        }

        UserPreferences prefs = preferencesService.get(userId);
        Instant now = Instant.now();
        Instant todayStart = computeTodayStart(prefs);

        long reviewedToday = cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(deckId, todayStart);
        long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart);
        long remaining = cardRepository.countDueCardsByTagsId(deckId, now);
        Instant nextDueAt = cardRepository.findFirstDueByTagsIdAndDueAfter(deckId, now);

        Map<String, Object> result = new HashMap<>();
        result.put("reviewedToday", reviewedToday);
        result.put("remaining", remaining);
        result.put("learnedToday", learnedToday);
        result.put("dailyLimit", prefs.getNewCardDailyLimit());
        result.put("nextDueAt", nextDueAt != null ? nextDueAt.toString() : null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/review/start")
    public ResponseEntity<Map<String, Object>> startReview(
            @RequestParam String deckId,
            @RequestParam(defaultValue = "STANDARD") String mode) {
        String userId = getUserId();
        var card = reviewService.getNextCard(deckId, mode, userId);
        var stats = reviewService.computeReviewStats(deckId, mode, userId);
        Map<String, Object> result = new HashMap<>();
        if (card.isPresent()) {
            result.put("card", cardToMap(card.get()));
            Map<Rating, CardState> preview = reviewService.previewCard(card.get(), Instant.now());
            result.put("preview", previewToMap(preview));
        } else {
            result.put("card", null);
        }
        result.put("stats", statsToMap(stats));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cards/{cardId}/forget")
    public ResponseEntity<Map<String, Object>> forgetCard(@PathVariable String cardId) {
        String userId = getUserId();
        reviewService.forgetCard(cardId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("id", cardId);
        response.put("cardState", 0);
        response.put("deletedReviewCount", 0);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/forget")
    public ResponseEntity<Map<String, Object>> forgetDeck(@RequestParam String deckId) {
        String userId = getUserId();
        ForgetDeckResult result = reviewService.forgetDeck(deckId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("cardCount", result.cardCount());
        response.put("totalDeletedReviewCount", result.deletedReviewCount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences() {
        String userId = getUserId();
        UserPreferences prefs = preferencesService.get(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("lastDeckId", prefs.getLastDeckId());
        result.put("lastMode", prefs.getLastMode());
        result.put("newCardDailyLimit", prefs.getNewCardDailyLimit());
        result.put("dayStartHour", prefs.getDayStartHour());
        result.put("timezone", prefs.getTimezone());
        result.put("learningSteps", prefs.getLearningSteps());
        result.put("relearningSteps", prefs.getRelearningSteps());
        result.put("desiredRetention", prefs.getDesiredRetention());
        result.put("maximumInterval", prefs.getMaximumInterval());
        result.put("enableFuzz", prefs.getEnableFuzz());
        result.put("shuffleDueCards", prefs.getShuffleDueCards());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/user/preferences")
    public ResponseEntity<Map<String, Object>> savePreferences(@RequestBody Map<String, Object> body) {
        String userId = getUserId();
        UserPreferences prefs = preferencesService.get(userId);
        if (body.containsKey("lastDeckId")) {
            prefs.setLastDeckId((String) body.get("lastDeckId"));
        }
        if (body.containsKey("lastMode")) {
            prefs.setLastMode((String) body.get("lastMode"));
        }
        if (body.containsKey("newCardDailyLimit")) {
            prefs.setNewCardDailyLimit(((Number) body.get("newCardDailyLimit")).intValue());
        }
        if (body.containsKey("dayStartHour")) {
            prefs.setDayStartHour(((Number) body.get("dayStartHour")).intValue());
        }
        if (body.containsKey("timezone")) {
            prefs.setTimezone((String) body.get("timezone"));
        }
        if (body.containsKey("learningSteps")) {
            prefs.setLearningSteps((String) body.get("learningSteps"));
        }
        if (body.containsKey("relearningSteps")) {
            prefs.setRelearningSteps((String) body.get("relearningSteps"));
        }
        if (body.containsKey("desiredRetention")) {
            Object val = body.get("desiredRetention");
            prefs.setDesiredRetention(val != null ? ((Number) val).doubleValue() : null);
        }
        if (body.containsKey("maximumInterval")) {
            Object val = body.get("maximumInterval");
            prefs.setMaximumInterval(val != null ? ((Number) val).intValue() : null);
        }
        if (body.containsKey("enableFuzz")) {
            prefs.setEnableFuzz((Boolean) body.get("enableFuzz"));
        }
        if (body.containsKey("shuffleDueCards")) {
            prefs.setShuffleDueCards((Boolean) body.get("shuffleDueCards"));
        }
        preferencesService.save(prefs);

        Map<String, Object> result = new HashMap<>();
        result.put("lastDeckId", prefs.getLastDeckId());
        result.put("lastMode", prefs.getLastMode());
        result.put("newCardDailyLimit", prefs.getNewCardDailyLimit());
        result.put("dayStartHour", prefs.getDayStartHour());
        result.put("timezone", prefs.getTimezone());
        result.put("learningSteps", prefs.getLearningSteps());
        result.put("relearningSteps", prefs.getRelearningSteps());
        result.put("desiredRetention", prefs.getDesiredRetention());
        result.put("maximumInterval", prefs.getMaximumInterval());
        result.put("enableFuzz", prefs.getEnableFuzz());
        result.put("shuffleDueCards", prefs.getShuffleDueCards());
        return ResponseEntity.ok(result);
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

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private Map<String, Object> cardToMap(Card card) {
        List<TagResponse> tags = card.getTags().stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getType()))
                .toList();
        Map<String, Object> map = new HashMap<>();
        map.put("id", card.getId());
        map.put("front", card.getFront());
        map.put("back", card.getBack());
        map.put("tags", tags);
        map.put("due", card.getDue() != null ? card.getDue().toString() : null);
        map.put("cardState", card.getCardState());
        map.put("step", card.getStep());
        map.put("stability", card.getStability());
        map.put("difficulty", card.getDifficulty());
        map.put("reps", card.getReps());
        map.put("lapses", card.getLapses());
        map.put("lastReview", card.getLastReview() != null ? card.getLastReview().toString() : null);
        map.put("firstReviewDate", card.getFirstReviewDate() != null ? card.getFirstReviewDate().toString() : null);
        map.put("createTime", card.getCreateTime() != null ? card.getCreateTime().toString() : null);
        return map;
    }

    private Map<String, Object> statsToMap(com.hugosol.chatagent.service.ReviewStats stats) {
        Map<String, Object> map = new HashMap<>();
        map.put("reviewedToday", stats.reviewedToday());
        map.put("remaining", stats.remaining());
        map.put("learnedToday", stats.learnedToday());
        map.put("dailyLimit", stats.dailyLimit());
        map.put("nextDueAt", stats.nextDueAt() != null ? stats.nextDueAt().toString() : null);
        return map;
    }

    private Map<String, Object> previewToMap(Map<Rating, CardState> preview) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<Rating, CardState> entry : preview.entrySet()) {
            result.put(entry.getKey().name(), cardStateToMap(entry.getValue()));
        }
        return result;
    }

    private Map<String, Object> cardStateToMap(CardState state) {
        Map<String, Object> map = new HashMap<>();
        map.put("stability", state.stability());
        map.put("difficulty", state.difficulty());
        map.put("state", state.state());
        map.put("step", state.step());
        map.put("due", state.due().toString());
        map.put("reps", state.reps());
        map.put("lapses", state.lapses());
        map.put("lastReview", state.lastReview() != null ? state.lastReview().toString() : null);
        map.put("elapsedDays", state.elapsedDays());
        return map;
    }
}
