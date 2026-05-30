package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, String> {
    Optional<UserProgress> findByUserId(String userId);
}
