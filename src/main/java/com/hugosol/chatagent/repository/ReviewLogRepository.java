package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, String> {

    List<ReviewLog> findByUserIdAndCardIdOrderByReviewedAtAsc(String userId, String cardId);

    List<ReviewLog> findByUserIdOrderByReviewedAtAsc(String userId);
}
