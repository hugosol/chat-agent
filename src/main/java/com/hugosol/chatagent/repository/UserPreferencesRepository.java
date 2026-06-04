package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, String> {
    Optional<UserPreferences> findByUserId(String userId);
}
