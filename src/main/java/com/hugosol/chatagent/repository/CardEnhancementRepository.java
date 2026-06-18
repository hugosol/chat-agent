package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.CardEnhancement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardEnhancementRepository extends JpaRepository<CardEnhancement, String> {

    List<CardEnhancement> findByCardId(String cardId);

    void deleteByCardId(String cardId);
}
