package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, String> {

    List<ReviewLog> findByUserIdAndCardIdOrderByReviewedAtAsc(String userId, String cardId);

    List<ReviewLog> findByUserIdOrderByReviewedAtAsc(String userId);

    void deleteByCardId(String cardId);

    void deleteByCardIdIn(List<String> cardIds);

    int countByCardId(String cardId);

    @Query("SELECT DISTINCT r.cardId FROM ReviewLog r WHERE r.userId = :userId")
    List<String> findDistinctCardIdsByUserId(@Param("userId") String userId);

    int countByUserId(String userId);
}
