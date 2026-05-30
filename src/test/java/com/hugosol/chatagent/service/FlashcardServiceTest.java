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
    void createCard_savesCardWithFsrsInitAndUpsertsTags() {
        var service = new FlashcardService(cardRepository, tagRepository);

        when(tagRepository.findByNameAndUserId("greeting", "user-1"))
                .thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Card result = service.createCard("hello", "world", List.of("greeting"), "user-1");

        assertThat(result.getFront()).isEqualTo("hello");
        assertThat(result.getBack()).isEqualTo("world");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getStability()).isEqualTo(2.5);
        assertThat(result.getDifficulty()).isEqualTo(0.0);
        assertThat(result.getCardState()).isEqualTo(0);
        assertThat(result.getReps()).isEqualTo(0);
        assertThat(result.getLapses()).isEqualTo(0);
        assertThat(result.getDue()).isNotNull();
        assertThat(result.getLastReview()).isNull();
        assertThat(result.getTags()).hasSize(1);
        assertThat(result.getTags().iterator().next().getName()).isEqualTo("greeting");

        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_reusesExistingTag() {
        var service = new FlashcardService(cardRepository, tagRepository);
        Tag existingTag = new Tag("greeting", "user-1");

        when(tagRepository.findByNameAndUserId("greeting", "user-1"))
                .thenReturn(Optional.of(existingTag));
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Card result = service.createCard("hello", "world", List.of("greeting"), "user-1");

        assertThat(result.getTags()).hasSize(1);
        assertThat(result.getTags().iterator().next()).isSameAs(existingTag);
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
    void createCard_emptyTags_throwsBadRequest(List<String> tagNames) {
        var service = new FlashcardService(cardRepository, tagRepository);

        assertThatThrownBy(() -> service.createCard("hello", "world", tagNames, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(cardRepository, never()).save(any());
        verify(tagRepository, never()).save(any());
    }
}
