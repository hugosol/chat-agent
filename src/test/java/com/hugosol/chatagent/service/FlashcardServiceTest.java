package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashcardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TagRepository tagRepository;

    @Test
    void createCard_savesCardWithFsrsInitAndDeckTag() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-tag-id");
        deckTag.setType("deck");

        when(tagRepository.findById("deck-tag-id"))
                .thenReturn(Optional.of(deckTag));
        when(cardRepository.findConflictingTagInfoByFront("hello", "user-1"))
                .thenReturn(List.of());
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Card result = service.createCard("hello", "world", List.of("deck-tag-id"), "user-1");

        assertThat(result.getFront()).isEqualTo("hello");
        assertThat(result.getBack()).isEqualTo("world");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getStability()).isEqualTo(2.5);
        assertThat(result.getDifficulty()).isEqualTo(0.0);
        assertThat(result.getCardState()).isEqualTo(0);
        assertThat(result.getReps()).isEqualTo(0);
        assertThat(result.getLapses()).isEqualTo(0);
        assertThat(result.getStep()).isEqualTo(-1);
        assertThat(result.getDue()).isNotNull();
        assertThat(result.getLastReview()).isNull();
        assertThat(result.getTags()).hasSize(1);
        assertThat(result.getTags().iterator().next().getName()).isEqualTo("daily");

        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_acceptsBothDeckAndNormalTags() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-id");
        deckTag.setType("deck");
        Tag normalTag = new Tag("verb", "user-1");
        normalTag.setId("tag-id");
        normalTag.setType(null);

        when(tagRepository.findById("deck-id"))
                .thenReturn(Optional.of(deckTag));
        when(tagRepository.findById("tag-id"))
                .thenReturn(Optional.of(normalTag));
        when(cardRepository.findConflictingTagInfoByFront("hello", "user-1"))
                .thenReturn(List.of());
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Card result = service.createCard("hello", "world", List.of("deck-id", "tag-id"), "user-1");

        assertThat(result.getTags()).hasSize(2);
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_missingDeckTag_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag normalTag = new Tag("verb", "user-1");
        normalTag.setId("tag-id");

        when(tagRepository.findById("tag-id"))
                .thenReturn(Optional.of(normalTag));

        assertThatThrownBy(() -> service.createCard("hello", "world", List.of("tag-id"), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void createCard_tagIdNotFound_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        when(tagRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCard("hello", "world", List.of("nonexistent"), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void createCard_tagNotOwnedByUser_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag otherUsersTag = new Tag("deck", "other-user");
        otherUsersTag.setId("deck-id");
        otherUsersTag.setType("deck");

        when(tagRepository.findById("deck-id"))
                .thenReturn(Optional.of(otherUsersTag));

        assertThatThrownBy(() -> service.createCard("hello", "world", List.of("deck-id"), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void getTags_returnsUserTags() {
        var service = new FlashcardService(cardRepository, tagRepository);
        when(tagRepository.findByUserId("user-1"))
                .thenReturn(List.of(new Tag("daily", "user-1"), new Tag("time", "user-1")));

        List<Tag> tags = service.getTags("user-1");

        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).getName()).isEqualTo("daily");
        assertThat(tags.get(1).getName()).isEqualTo("time");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void createCard_emptyTags_throws422(List<String> tagIds) {
        var service = new FlashcardService(cardRepository, tagRepository);

        assertThatThrownBy(() -> service.createCard("hello", "world", tagIds, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
        verify(tagRepository, never()).findById(any());
    }

    @Test
    void createCard_sameDeckConflict_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-id");
        deckTag.setType("deck");

        when(tagRepository.findById("deck-id"))
                .thenReturn(Optional.of(deckTag));
        when(cardRepository.findConflictingTagInfoByFront("hello", "user-1"))
                .thenReturn(java.util.Collections.singletonList(new Object[]{"deck-id", "daily"}));

        assertThatThrownBy(() -> service.createCard("hello", "world", List.of("deck-id"), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void createCard_crossDeckConflict_allowsCreation() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-id");
        deckTag.setType("deck");

        when(tagRepository.findById("deck-id"))
                .thenReturn(Optional.of(deckTag));
        // Card exists in other-deck but NOT in deck-id
        when(cardRepository.findConflictingTagInfoByFront("hello", "user-1"))
                .thenReturn(java.util.Collections.singletonList(new Object[]{"other-deck-id", "Other Deck"}));
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Card result = service.createCard("hello", "world", List.of("deck-id"), "user-1");

        assertThat(result.getFront()).isEqualTo("hello");
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void updateCardBack_updatesBackAndReturnsCard() {
        var service = new FlashcardService(cardRepository, tagRepository);
        Card card = new Card("user-1", "hello", "old back");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = service.updateCardBack("user-1", "card-1", "new back");

        assertThat(result.getBack()).isEqualTo("new back");
        assertThat(result.getFront()).isEqualTo("hello");
        verify(cardRepository).save(card);
    }

    @Test
    void updateCardBack_emptyBack_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        assertThatThrownBy(() -> service.updateCardBack("user-1", "card-1", "   "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void updateCardBack_cardNotFound_throws404() {
        var service = new FlashcardService(cardRepository, tagRepository);
        when(cardRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCardBack("user-1", "nonexistent", "new back"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCardBack_wrongUser_throws404() {
        var service = new FlashcardService(cardRepository, tagRepository);
        Card card = new Card("other-user", "hello", "old back");
        card.setId("card-1");
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateCardBack("user-1", "card-1", "new back"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCard_frontConflict_throws422() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Card card = new Card("user-1", "hello", "world");
        card.setId("card-1");

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-id");
        deckTag.setType("deck");

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(tagRepository.findById("deck-id")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findConflictingTagInfoByFrontExcludingId("newword", "user-1", "card-1"))
                .thenReturn(java.util.Collections.singletonList(new Object[]{"deck-id", "daily"}));

        assertThatThrownBy(() -> service.updateCard("user-1", "card-1", "newword", "world", List.of("deck-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void updateCard_crossDeckConflict_allowsUpdate() {
        var service = new FlashcardService(cardRepository, tagRepository);

        Card card = new Card("user-1", "hello", "world");
        card.setId("card-1");

        Tag deckTag = new Tag("daily", "user-1");
        deckTag.setId("deck-id");
        deckTag.setType("deck");

        when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(tagRepository.findById("deck-id")).thenReturn(Optional.of(deckTag));
        // Conflict exists only in other-deck, not in deck-id
        when(cardRepository.findConflictingTagInfoByFrontExcludingId("newword", "user-1", "card-1"))
                .thenReturn(java.util.Collections.singletonList(new Object[]{"other-deck-id", "Other Deck"}));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        Card result = service.updateCard("user-1", "card-1", "newword", "world", List.of("deck-id"));

        assertThat(result.getFront()).isEqualTo("newword");
        verify(cardRepository).save(any(Card.class));
    }
}
