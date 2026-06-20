package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.service.CardEnhanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CardEnhanceController {

    private final CardEnhanceService cardEnhanceService;

    public CardEnhanceController(CardEnhanceService cardEnhanceService) {
        this.cardEnhanceService = cardEnhanceService;
    }

    @PostMapping("/cards/{id}/enhance")
    public ResponseEntity<Map<String, Object>> enhance(@PathVariable String id) {
        String userId = getUserId();
        CardEnhanceService.EnhanceResult result = cardEnhanceService.enhance(id, userId);

        Map<String, Object> response = new HashMap<>();
        if (result.movieQuote() != null) {
            Map<String, Object> quote = new HashMap<>();
            quote.put("movieTitle", result.movieQuote().movieTitle());
            quote.put("imdbId", result.movieQuote().imdbId());
            quote.put("quote", result.movieQuote().quote());
            quote.put("timestamp", result.movieQuote().timestamp());
            response.put("movieQuote", quote);
        }
        response.put("sceneSummary", result.sceneSummary());
        response.put("etymology", result.etymology());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/{id}/enhance/requote")
    public ResponseEntity<Map<String, Object>> requote(@PathVariable String id,
                                                        @RequestBody Map<String, String> body) {
        String userId = getUserId();
        String excludeImdbId = body.get("imdbId");
        String excludeTimestamp = body.get("timestamp");
        CardEnhanceService.EnhanceResult result = cardEnhanceService.requote(id, userId, excludeImdbId, excludeTimestamp);

        Map<String, Object> response = new HashMap<>();
        if (result.movieQuote() == null) {
            response.put("found", false);
            if (result.notFoundReason() != null) {
                response.put("reason", result.notFoundReason());
            }
        } else {
            if (result.movieQuote() != null) {
                Map<String, Object> quote = new HashMap<>();
                quote.put("movieTitle", result.movieQuote().movieTitle());
                quote.put("imdbId", result.movieQuote().imdbId());
                quote.put("quote", result.movieQuote().quote());
                quote.put("timestamp", result.movieQuote().timestamp());
                response.put("movieQuote", quote);
            }
            if (result.sceneSummary() != null) {
                response.put("sceneSummary", result.sceneSummary());
            }
            response.put("found", true);
        }
        return ResponseEntity.ok(response);
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
