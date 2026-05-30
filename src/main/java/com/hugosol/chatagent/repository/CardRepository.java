package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, String> {
}
