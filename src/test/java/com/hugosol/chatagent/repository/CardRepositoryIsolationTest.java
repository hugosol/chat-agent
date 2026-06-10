package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class CardRepositoryIsolationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TestEntityManager em;

    private Tag deckA;
    private Tag deckB;
    private String userIdA = "userA";
    private String userIdB = "userB";
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        deckA = new Tag("deckA", userIdA);
        deckA.setType("deck");
        deckA = em.persistAndFlush(deckA);

        deckB = new Tag("deckB", userIdB);
        deckB.setType("deck");
        deckB = em.persistAndFlush(deckB);

        Card cardA = new Card(userIdA, "hello", "world");
        cardA.setDue(now.minusSeconds(3600));
        cardA.setCardState(2);
        cardA.setTags(Set.of(deckA));
        cardA.setLastReview(now);
        cardA.setFirstReviewDate(now);
        em.persistAndFlush(cardA);

        Card cardB = new Card(userIdB, "你好", "世界");
        cardB.setDue(now.minusSeconds(3600));
        cardB.setCardState(2);
        cardB.setTags(Set.of(deckB));
        cardB.setLastReview(now);
        cardB.setFirstReviewDate(now);
        em.persistAndFlush(cardB);
    }

    @Test
    void findFirstDueCardByDeckId_shouldNotReturnOtherUserCard() {
        var resultA = cardRepository.findFirstDueCardByDeckId(deckA.getId(), now, userIdA);
        assertThat(resultA).isPresent();
        assertThat(resultA.get().getUserId()).isEqualTo(userIdA);

        var resultWrong = cardRepository.findFirstDueCardByDeckId(deckA.getId(), now, userIdB);
        assertThat(resultWrong).isEmpty();
    }

    @Test
    void findFirstNewCardByDeckId_shouldNotReturnOtherUserCard() {
        var resultWrong = cardRepository.findFirstNewCardByDeckId(deckA.getId(), userIdB);
        assertThat(resultWrong).isEmpty();
    }

    @Test
    void countByTagsId_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countByTagsId(deckA.getId(), userIdB);
        assertThat(countB).isEqualTo(0);

        long countA = cardRepository.countByTagsId(deckA.getId(), userIdA);
        assertThat(countA).isEqualTo(1);
    }

    @Test
    void countDueCardsByTagsId_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countDueCardsByTagsId(deckA.getId(), now, userIdB);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    void countByTagsIdAndCardState_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countByTagsIdAndCardState(deckA.getId(), 2, userIdB);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    void countByTagsIdAndLastReviewGreaterThanEqual_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countByTagsIdAndLastReviewGreaterThanEqual(
                deckA.getId(), now.minusSeconds(86400), userIdB);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    void countByTagsIdAndFirstReviewDateGreaterThanEqual_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(
                deckA.getId(), now.minusSeconds(86400), userIdB);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    void findFirstDueByTagsIdAndDueAfter_shouldNotReturnOtherUserCard() {
        var resultB = cardRepository.findFirstDueByTagsIdAndDueAfter(
                deckA.getId(), now.minusSeconds(7200), userIdB);
        assertThat(resultB).isNull();
    }

    @Test
    void findRandomCardByDeckId_shouldNotReturnOtherUserCard() {
        var resultB = cardRepository.findRandomCardByDeckId(deckA.getId(), userIdB);
        assertThat(resultB).isEmpty();
    }

    @Test
    void findRandomDueCardByDeckId_shouldNotReturnOtherUserCard() {
        var resultB = cardRepository.findRandomDueCardByDeckId(
                deckA.getId(), now, userIdB);
        assertThat(resultB).isEmpty();
    }

    @Test
    void findRandomNewCardByDeckId_shouldNotReturnOtherUserCard() {
        var resultB = cardRepository.findRandomNewCardByDeckId(deckA.getId(), userIdB);
        assertThat(resultB).isEmpty();
    }

    @Test
    void countByTagsIdAndCardStateNotAndDueLessThanEqual_shouldNotCountOtherUserCards() {
        long countB = cardRepository.countByTagsIdAndCardStateNotAndDueLessThanEqual(
                deckA.getId(), 0, now, userIdB);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    void countDueAndNewByDeckId_shouldNotCountOtherUserCards() {
        List<Object[]> results = cardRepository.countDueAndNewByDeckId(deckA.getId(), now, userIdB);
        Object[] counts = results.get(0);
        assertThat(((Number) counts[0]).longValue()).isEqualTo(0);
        assertThat(((Number) counts[1]).longValue()).isEqualTo(0);
    }

    @Test
    void countDueAndNewByDeckId_separatesDueAndNew() {
        Card newCard = new Card(userIdA, "new", "card");
        newCard.setCardState(0);
        newCard.setTags(Set.of(deckA));
        em.persistAndFlush(newCard);

        List<Object[]> results = cardRepository.countDueAndNewByDeckId(deckA.getId(), now, userIdA);
        Object[] counts = results.get(0);

        assertThat(((Number) counts[0]).longValue()).isEqualTo(1);
        assertThat(((Number) counts[1]).longValue()).isEqualTo(1);
    }

    @Test
    void countDueAndNewByDeckId_excludesFutureDueCards() {
        Card futureCard = new Card(userIdA, "future", "card");
        futureCard.setCardState(2);
        futureCard.setDue(now.plusSeconds(3600));
        futureCard.setTags(Set.of(deckA));
        em.persistAndFlush(futureCard);

        List<Object[]> results = cardRepository.countDueAndNewByDeckId(deckA.getId(), now, userIdA);
        Object[] counts = results.get(0);

        assertThat(((Number) counts[0]).longValue()).isEqualTo(1);
        assertThat(((Number) counts[1]).longValue()).isEqualTo(0);
    }

    @Test
    void countDueAndNewByDeckId_emptyDeckReturnsZero() {
        List<Object[]> results = cardRepository.countDueAndNewByDeckId("non-existent-deck", now, userIdA);
        Object[] counts = results.get(0);
        assertThat(((Number) counts[0]).longValue()).isEqualTo(0);
        assertThat(((Number) counts[1]).longValue()).isEqualTo(0);
    }

    @Test
    void countDueAndNewByDeckId_returnsNumberTypes() {
        List<Object[]> results = cardRepository.countDueAndNewByDeckId(deckA.getId(), now, userIdA);
        Object[] counts = results.get(0);
        assertThat(counts[0]).isInstanceOf(Number.class);
        assertThat(counts[1]).isInstanceOf(Number.class);
    }
}