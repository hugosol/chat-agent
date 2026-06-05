package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.FsrsParametersRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class FsrsConfigService {

    private final FsrsParametersRepository fsrsParametersRepository;
    private final UserPreferencesService userPreferencesService;

    public FsrsConfigService(FsrsParametersRepository fsrsParametersRepository,
                             UserPreferencesService userPreferencesService) {
        this.fsrsParametersRepository = fsrsParametersRepository;
        this.userPreferencesService = userPreferencesService;
    }

    @Cacheable(value = "fsrsConfig", key = "#userId")
    public FsrsSchedulerConfig getConfig(String userId) {
        FsrsParameters params = fsrsParametersRepository.findByUserId(userId).orElse(null);
        UserPreferences prefs = userPreferencesService.get(userId);
        return FsrsSchedulerConfig.merge(params, prefs);
    }
}
