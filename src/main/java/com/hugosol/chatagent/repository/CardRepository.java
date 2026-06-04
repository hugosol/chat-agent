package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, String>, JpaSpecificationExecutor<Card> {
    Optional<Card> findByFrontIgnoreCaseAndUserId(String front, String userId);

    Optional<Card> findByFrontIgnoreCaseAndUserIdAndIdNot(String front, String userId, String id);

    List<Card> findAllByTagsContaining(Tag tag);

    @Query("SELECT LOWER(c.front) FROM Card c WHERE LOWER(c.front) IN (:fronts) AND c.userId = :userId")
    List<String> findExistingFronts(@Param("fronts") List<String> fronts, @Param("userId") String userId);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.lastReview >= :since")
    long countByTagsIdAndLastReviewGreaterThanEqual(@Param("tagId") String tagId, @Param("since") Instant since);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.cardState <> :excludeState AND c.due <= :now")
    long countByTagsIdAndCardStateNotAndDueLessThanEqual(@Param("tagId") String tagId, @Param("excludeState") int excludeState, @Param("now") Instant now);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.firstReviewDate >= :since")
    long countByTagsIdAndFirstReviewDateGreaterThanEqual(@Param("tagId") String tagId, @Param("since") Instant since);

    @Query("SELECT MIN(c.due) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.due > :now")
    Instant findFirstDueByTagsIdAndDueAfter(@Param("tagId") String tagId, @Param("now") Instant now);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.cardState <> 0 AND c.due <= :now")
    long countDueCardsByTagsId(@Param("tagId") String tagId, @Param("now") Instant now);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId")
    long countByTagsId(@Param("tagId") String tagId);

    @Query("SELECT COUNT(c) FROM Card c JOIN c.tags t WHERE t.id = :tagId AND c.cardState = :cardState")
    long countByTagsIdAndCardState(@Param("tagId") String tagId, @Param("cardState") int cardState);

    @Query("SELECT c FROM Card c JOIN c.tags t WHERE t.id = :deckId AND c.cardState <> 0 AND c.due <= :now ORDER BY c.due ASC LIMIT 1")
    Optional<Card> findFirstDueCardByDeckId(@Param("deckId") String deckId, @Param("now") Instant now);

    @Query("SELECT c FROM Card c JOIN c.tags t WHERE t.id = :deckId AND c.cardState = 0 ORDER BY c.createTime ASC LIMIT 1")
    Optional<Card> findFirstNewCardByDeckId(@Param("deckId") String deckId);

    @Query(value = "SELECT c.* FROM cards c JOIN card_tags ct ON c.id = ct.card_id WHERE ct.tag_id = :deckId ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<Card> findRandomCardByDeckId(@Param("deckId") String deckId);
}
