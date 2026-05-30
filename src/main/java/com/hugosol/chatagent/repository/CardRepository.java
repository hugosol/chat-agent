package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, String> {
    Optional<Card> findByFrontIgnoreCaseAndUserId(String front, String userId);
}
