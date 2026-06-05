package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.UserPreferencesRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesService {

    private final UserPreferencesRepository preferencesRepository;

    public UserPreferencesService(UserPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    public UserPreferences get(String userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreferences defaults = new UserPreferences(userId);
                    return preferencesRepository.save(defaults);
                });
    }

    @Transactional
    @CacheEvict(value = "fsrsConfig", key = "#preferences.userId")
    public UserPreferences save(UserPreferences preferences) {
        return preferencesRepository.save(preferences);
    }
}
