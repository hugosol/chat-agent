package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.AddCardRequest;
import com.hugosol.chatagent.dto.AddCardResponse;
import com.hugosol.chatagent.dto.TagResponse;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.service.FlashcardService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @PostMapping("/cards/add")
    public ResponseEntity<AddCardResponse> addCard(@RequestBody AddCardRequest request) {
        String userId = getUserId();
        Card card = flashcardService.createCard(request.front(), request.back(), request.tags(), userId);
        return ResponseEntity.ok(toAddCardResponse(card));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags() {
        String userId = getUserId();
        List<Tag> tags = flashcardService.getTags(userId);
        List<TagResponse> response = tags.stream()
                .map(t -> new TagResponse(t.getName(), t.getType()))
                .toList();
        return ResponseEntity.ok(response);
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private static AddCardResponse toAddCardResponse(Card card) {
        List<TagResponse> tags = card.getTags().stream()
                .map(t -> new TagResponse(t.getName(), t.getType()))
                .toList();
        return new AddCardResponse(card.getId(), card.getFront(), card.getBack(), tags, card.getDue().toString());
    }
}
