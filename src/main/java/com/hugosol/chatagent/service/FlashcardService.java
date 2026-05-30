package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FlashcardService {

    private final CardRepository cardRepository;
    private final TagRepository tagRepository;

    public FlashcardService(CardRepository cardRepository, TagRepository tagRepository) {
        this.cardRepository = cardRepository;
        this.tagRepository = tagRepository;
    }

    public Card createCard(String front, String back, List<String> tagNames, String userId) {
        Card card = new Card(userId, front, back);

        var state = FsrsScheduler.createInitState(Instant.now());
        card.setStability(state.stability());
        card.setDifficulty(state.difficulty());
        card.setCardState(state.state());
        card.setDue(state.due());
        card.setReps(state.reps());
        card.setLapses(state.lapses());

        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            Tag tag = tagRepository.findByNameAndUserId(name, userId)
                    .orElseGet(() -> tagRepository.save(new Tag(name, userId)));
            tags.add(tag);
        }
        card.setTags(tags);

        return cardRepository.save(card);
    }

    public List<Tag> getTags(String userId) {
        return tagRepository.findByUserId(userId);
    }
}
