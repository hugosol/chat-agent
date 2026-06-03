package com.hugosol.chatagent.service.card;

import com.hugosol.chatagent.dto.ImportError;
import com.hugosol.chatagent.dto.ImportResult;
import com.hugosol.chatagent.model.BatchOperationLog;
import com.hugosol.chatagent.model.BatchOperationStatus;
import com.hugosol.chatagent.model.BatchOperationType;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.BatchOperationLogRepository;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;
import com.hugosol.chatagent.service.card.CardBatchService.ExportData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardBatchServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private BatchOperationLogRepository batchOperationLogRepository;

    private final CardCsvParser cardCsvParser = new CardCsvParser();

    @Test
    void importCards_allValid_successfullyImportsAllCards() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findExistingFronts(List.of("hello", "goodbye"), "user-1"))
                .thenReturn(List.of());
        when(cardRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        byte[] csvBytes = ("front,back,stability,difficulty,cardState,due,reps,lapses,lastReview\n" +
                "hello,world,3.0,0.3,Review,2024-06-01T10:00:00Z,5,2,2024-05-01T10:00:00Z\n" +
                "goodbye,再见,2.5,0.1,New,2024-06-02T10:00:00Z,0,0,\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();

        ArgumentCaptor<List> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(cardsCaptor.capture());
        assertThat(cardsCaptor.getValue()).hasSize(2);

        ArgumentCaptor<BatchOperationLog> logCaptor = ArgumentCaptor.forClass(BatchOperationLog.class);
        verify(batchOperationLogRepository).save(logCaptor.capture());
        BatchOperationLog log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(BatchOperationStatus.SUCCESS);
        assertThat(log.getOperationType()).isEqualTo(BatchOperationType.IMPORT);
        assertThat(log.getTotalRows()).isEqualTo(2);
        assertThat(log.getSuccessCount()).isEqualTo(2);
        assertThat(log.getSkipCount()).isEqualTo(0);
    }

    @Test
    void importCards_frontDuplicateInCsv_returnsErrors() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back\n" +
                "hello,world\n" +
                "hello,again\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("CSV文件内front重复");

        verify(cardRepository, never()).saveAll(anyList());

        ArgumentCaptor<BatchOperationLog> logCaptor = ArgumentCaptor.forClass(BatchOperationLog.class);
        verify(batchOperationLogRepository).save(logCaptor.capture());
        BatchOperationLog log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(BatchOperationStatus.FAILED);
    }

    @Test
    void importCards_dbDuplicate_returnsErrors() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findExistingFronts(List.of("hello"), "user-1"))
                .thenReturn(List.of("hello"));

        byte[] csvBytes = ("front,back\n" +
                "hello,world\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).isEqualTo("卡片已存在");

        verify(cardRepository, never()).saveAll(anyList());
    }

    @Test
    void importCards_emptyFront_returnsError() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back\n" +
                "  ,world\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("正面不能为空");
    }

    @Test
    void importCards_emptyBack_returnsError() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back\n" +
                "hello,\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("背面不能为空");
    }

    @Test
    void importCards_invalidFsrs_returnsError() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back,stability,difficulty\n" +
                "hello,world,-1.0,2.0\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("stability必须大于0");
    }

    @Test
    void importCards_invalidDifficulty_returnsError() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back,difficulty\n" +
                "hello,world,2.0\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("difficulty必须在0到1之间");
    }

    @Test
    void importCards_invalidDueFormat_returnsError() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back,due\n" +
                "hello,world,not-a-date\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).reason()).contains("due格式无效");
    }

    @Test
    void importCards_tagNotFound_throwsException() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        when(tagRepository.findById("nonexistent")).thenReturn(Optional.empty());

        byte[] csvBytes = "front,back\nhello,world\n".getBytes(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.importCards(csvBytes, "cards.csv", "nonexistent", "user-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("标签不存在");

        verify(cardRepository, never()).saveAll(anyList());
    }

    @Test
    void importCards_tagNotDeckType_throwsException() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag normalTag = new Tag("normal", "user-1");
        normalTag.setId("tag-1");
        normalTag.setType(null);

        when(tagRepository.findById("tag-1")).thenReturn(Optional.of(normalTag));

        byte[] csvBytes = "front,back\nhello,world\n".getBytes(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.importCards(csvBytes, "cards.csv", "tag-1", "user-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("只能导入到牌组标签");

        verify(cardRepository, never()).saveAll(anyList());
    }

    @Test
    void importCards_tagWrongUser_throwsException() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag otherUserTag = new Tag("My Deck", "other-user");
        otherUserTag.setId("deck-1");
        otherUserTag.setType("deck");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(otherUserTag));

        byte[] csvBytes = "front,back\nhello,world\n".getBytes(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.importCards(csvBytes, "cards.csv", "deck-1", "user-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void importCards_csvWithoutFsrsFields_usesDefaults() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findExistingFronts(List.of("hello"), "user-1"))
                .thenReturn(List.of());
        when(cardRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        byte[] csvBytes = ("front,back\n" +
                "hello,world\n").getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();

        ArgumentCaptor<List> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(cardsCaptor.capture());
    }

    @Test
    void importCards_failedLog_hasErrorDetails() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));

        byte[] csvBytes = ("front,back\n" +
                "hello,world\n" +
                "hello,again\n").getBytes(StandardCharsets.UTF_8);

        service.importCards(csvBytes, "cards.csv", "deck-1", "user-1");

        ArgumentCaptor<BatchOperationLog> logCaptor = ArgumentCaptor.forClass(BatchOperationLog.class);
        verify(batchOperationLogRepository).save(logCaptor.capture());
        BatchOperationLog log = logCaptor.getValue();
        assertThat(log.getErrorDetails()).contains("\"row\":2");
        assertThat(log.getErrorDetails()).contains("\"front\":\"hello\"");
        assertThat(log.getStatus()).isEqualTo(BatchOperationStatus.FAILED);
    }

    @Test
    void exportCards_allCards_returnsCsv() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "My Deck", "user-1");

        Card card1 = createCard("user-1", "hello", "world", 3.0, 0.3, 2);
        Card card2 = createCard("user-1", "goodbye", "再见", 2.5, 0.1, 0);

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findAllByTagsContaining(deckTag))
                .thenReturn(List.of(card1, card2));

        ExportData data = service.exportCards("deck-1", "user-1");

        assertThat(data.fileName()).startsWith("My Deck_");
        assertThat(data.fileName()).endsWith(".csv");

        String csv = new String(data.csvBytes(), StandardCharsets.UTF_8);
        assertThat(csv).contains("front,back,stability,difficulty,cardState");
        assertThat(csv).contains("hello,world,3,0.3,Review");
        assertThat(csv).contains("goodbye,再见,2.5,0.1,New");

        ArgumentCaptor<BatchOperationLog> logCaptor = ArgumentCaptor.forClass(BatchOperationLog.class);
        verify(batchOperationLogRepository).save(logCaptor.capture());
        BatchOperationLog log = logCaptor.getValue();
        assertThat(log.getOperationType()).isEqualTo(BatchOperationType.EXPORT);
        assertThat(log.getStatus()).isEqualTo(BatchOperationStatus.SUCCESS);
        assertThat(log.getTotalRows()).isEqualTo(2);
    }

    @Test
    void exportCards_emptyTag_returnsCsvWithHeaderOnly() {
        var service = new CardBatchService(cardRepository, tagRepository, batchOperationLogRepository, cardCsvParser);

        Tag deckTag = createDeckTag("deck-1", "Empty Deck", "user-1");

        when(tagRepository.findById("deck-1")).thenReturn(Optional.of(deckTag));
        when(cardRepository.findAllByTagsContaining(deckTag))
                .thenReturn(List.of());

        ExportData data = service.exportCards("deck-1", "user-1");

        String csv = new String(data.csvBytes(), StandardCharsets.UTF_8);
        assertThat(csv).contains("front,back,stability,difficulty,cardState");
        assertThat(csv.split("\n")).hasSize(1);

        ArgumentCaptor<BatchOperationLog> logCaptor = ArgumentCaptor.forClass(BatchOperationLog.class);
        verify(batchOperationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTotalRows()).isEqualTo(0);
    }

    private Tag createDeckTag(String id, String name, String userId) {
        Tag tag = new Tag(name, userId);
        tag.setId(id);
        tag.setType("deck");
        return tag;
    }

    private Card createCard(String userId, String front, String back,
                            double stability, double difficulty, int cardState) {
        Card card = new Card(userId, front, back);
        card.setStability(stability);
        card.setDifficulty(difficulty);
        card.setCardState(cardState);
        card.setDue(java.time.Instant.parse("2024-06-01T10:00:00Z"));
        card.setReps(5);
        card.setLapses(2);
        card.setLastReview(java.time.Instant.parse("2024-05-01T10:00:00Z"));
        return card;
    }
}
