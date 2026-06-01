package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.AddCardRequest;
import com.hugosol.chatagent.dto.AddCardResponse;
import com.hugosol.chatagent.dto.CreateTagRequest;
import com.hugosol.chatagent.dto.TagResponse;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.service.FlashcardService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Card card = flashcardService.createCard(request.front(), request.back(), request.tagIds(), userId);
        return ResponseEntity.ok(toAddCardResponse(card));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags(@RequestParam(required = false) String type) {
        String userId = getUserId();
        List<Tag> tags;
        if (type != null) {
            tags = flashcardService.getTagsByType(userId, type);
        } else {
            tags = flashcardService.getTags(userId);
        }
        List<TagResponse> response = tags.stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getType()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tags")
    public ResponseEntity<TagResponse> createTag(@RequestBody CreateTagRequest request) {
        String userId = getUserId();
        Tag tag = flashcardService.createTag(userId, request.name(), request.type());
        return ResponseEntity.ok(new TagResponse(tag.getId(), tag.getName(), tag.getType()));
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<TagResponse> updateTag(@PathVariable String id, @RequestBody CreateTagRequest request) {
        String userId = getUserId();
        Tag tag = flashcardService.updateTag(userId, id, request.name(), request.type());
        return ResponseEntity.ok(new TagResponse(tag.getId(), tag.getName(), tag.getType()));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable String id) {
        String userId = getUserId();
        flashcardService.deleteTag(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cards")
    public ResponseEntity<Page<Map<String, Object>>> listCards(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String deckId,
            Pageable pageable) {
        String userId = getUserId();
        Page<Card> page = flashcardService.listCards(userId, search, deckId, pageable);

        Page<Map<String, Object>> response = page.map(card -> {
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
            map.put("createTime", card.getCreateTime() != null ? card.getCreateTime().toString() : null);
            return map;
        });

        return ResponseEntity.ok(response);
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<AddCardResponse> updateCard(@PathVariable String id, @RequestBody AddCardRequest request) {
        String userId = getUserId();
        Card card = flashcardService.updateCard(userId, id, request.front(), request.back(), request.tagIds());
        return ResponseEntity.ok(toAddCardResponse(card));
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable String id) {
        String userId = getUserId();
        flashcardService.deleteCard(userId, id);
        return ResponseEntity.ok().build();
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
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getType()))
                .toList();
        return new AddCardResponse(card.getId(), card.getFront(), card.getBack(), tags, card.getDue().toString());
    }
}
