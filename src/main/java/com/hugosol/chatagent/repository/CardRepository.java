package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, String>, JpaSpecificationExecutor<Card> {
    Optional<Card> findByFrontIgnoreCaseAndUserId(String front, String userId);

    Optional<Card> findByFrontIgnoreCaseAndUserIdAndIdNot(String front, String userId, String id);

    List<Card> findAllByTagsContaining(Tag tag);
}
