package com.hugosol.chatagent.service;

import com.hugosol.chatagent.dto.CheckCardResponse;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlashcardService {

    private final CardRepository cardRepository;
    private final TagRepository tagRepository;

    public FlashcardService(CardRepository cardRepository, TagRepository tagRepository) {
        this.cardRepository = cardRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public Card createCard(String front, String back, List<String> tagIds, String userId) {
        if (tagIds == null || tagIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不能为空");
        }

        Card card = new Card(userId, front, back);

        var state = FsrsScheduler.createInitState(Instant.now());
        card.setStability(state.stability());
        card.setDifficulty(state.difficulty());
        card.setCardState(state.state());
        card.setDue(state.due());
        card.setReps(state.reps());
        card.setLapses(state.lapses());
        card.setStep(state.step());

        Set<Tag> tags = new HashSet<>();
        boolean hasDeck = false;
        for (String tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在"));
            if (!tag.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在");
            }
            if ("deck".equals(tag.getType())) {
                hasDeck = true;
            }
            tags.add(tag);
        }

        if (!hasDeck) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "至少需要一个牌组标签");
        }

        List<Object[]> conflicts = cardRepository.findConflictingTagInfoByFront(front, userId);
        for (Object[] row : conflicts) {
            String conflictTagId = (String) row[0];
            if (tagIds.contains(conflictTagId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "卡片'" + front + "'在牌组'" + row[1] + "'中已存在");
            }
        }

        card.setTags(tags);

        return cardRepository.save(card);
    }

    public CheckCardResponse checkCard(String front, List<String> tagIds, String userId) {
        List<Object[]> rows = cardRepository.findConflictingTagInfoByFront(front, userId);
        List<CheckCardResponse.ConflictInfo> conflicts = rows.stream()
                .map(row -> new CheckCardResponse.ConflictInfo((String) row[0], (String) row[1]))
                .collect(Collectors.toList());
        return new CheckCardResponse(conflicts);
    }

    public List<Tag> getTags(String userId) {
        return tagRepository.findByUserId(userId);
    }

    public List<Tag> getTagsByType(String userId, String type) {
        return tagRepository.findByUserIdAndType(userId, type);
    }

    @Transactional
    public Tag createTag(String userId, String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签名不能为空");
        }
        if (tagRepository.findByNameIgnoreCaseAndUserId(name, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签'" + name + "'已存在");
        }
        Tag tag = new Tag(name.trim(), userId);
        tag.setType(type);
        return tagRepository.save(tag);
    }

    @Transactional
    public Tag updateTag(String userId, String tagId, String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签名不能为空");
        }
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!tag.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (tagRepository.findByNameIgnoreCaseAndUserIdAndIdNot(name, userId, tagId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签'" + name + "'已存在");
        }
        tag.setName(name.trim());
        tag.setType(type);
        return tagRepository.save(tag);
    }

    @Transactional
    public void deleteTag(String userId, String tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!tag.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var cardsWithTag = cardRepository.findAllByTagsContaining(tag);
        int orphanCount = 0;
        for (Card card : cardsWithTag) {
            boolean hasOtherDeck = card.getTags().stream()
                    .anyMatch(t -> !t.getId().equals(tagId) && "deck".equals(t.getType()));
            if (!hasOtherDeck) {
                orphanCount++;
            }
        }

        if (orphanCount > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "{\"orphanCount\":" + orphanCount + "}");
        }

        for (Card card : cardsWithTag) {
            card.getTags().remove(tag);
            cardRepository.save(card);
        }
        tagRepository.delete(tag);
    }

    public Page<Card> listCards(String userId, String search, String deckId, Pageable pageable) {
        Sort sort = pageable.getSort();
        Pageable pageableWithoutSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Specification<Card> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("front")), "%" + search.toLowerCase() + "%"));
            }

            if (deckId != null && !deckId.isBlank()) {
                Join<Card, Tag> tagsJoin = root.join("tags");
                predicates.add(cb.equal(tagsJoin.get("id"), deckId));
            }

            if (query != null && sort.isSorted()) {
                for (Sort.Order order : sort) {
                    if ("front".equals(order.getProperty())) {
                        query.orderBy(order.isAscending()
                                ? cb.asc(cb.lower(root.get("front")))
                                : cb.desc(cb.lower(root.get("front"))));
                    } else if ("createTime".equals(order.getProperty())) {
                        query.orderBy(order.isAscending()
                                ? cb.asc(root.get("createTime"))
                                : cb.desc(root.get("createTime")));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return cardRepository.findAll(spec, pageableWithoutSort);
    }

    @Transactional
    public Card updateCard(String userId, String cardId, String front, String back, List<String> tagIds) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (tagIds == null || tagIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不能为空");
        }

        card.setFront(front);
        card.setBack(back);

        Set<Tag> tags = new HashSet<>();
        boolean hasDeck = false;
        for (String tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在"));
            if (!tag.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在");
            }
            if ("deck".equals(tag.getType())) {
                hasDeck = true;
            }
            tags.add(tag);
        }

        if (!hasDeck) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "至少需要一个牌组标签");
        }

        List<Object[]> conflicts = cardRepository.findConflictingTagInfoByFrontExcludingId(front, userId, cardId);
        for (Object[] row : conflicts) {
            String conflictTagId = (String) row[0];
            if (tagIds.contains(conflictTagId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "卡片'" + front + "'在牌组'" + row[1] + "'中已存在");
            }
        }

        card.setTags(tags);
        return cardRepository.save(card);
    }

    @Transactional
    public Card updateCardBack(String userId, String cardId, String back) {
        if (back == null || back.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "卡片背面不能为空");
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        card.setBack(back.trim());
        return cardRepository.save(card);
    }

    @Transactional
    public void deleteCard(String userId, String cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        card.getTags().clear();
        cardRepository.delete(card);
    }
}
