package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.UserPreferences;

import org.springframework.stereotype.Service;

@Service
public class FsrsConfigService {

    private final FsrsParametersService fsrsParametersService;
    private final UserPreferencesService userPreferencesService;

    public FsrsConfigService(FsrsParametersService fsrsParametersService,
                             UserPreferencesService userPreferencesService) {
        this.fsrsParametersService = fsrsParametersService;
        this.userPreferencesService = userPreferencesService;
    }

    public FsrsSchedulerConfig getConfig(String userId) {
        FsrsParameters params = fsrsParametersService.get(userId);
        UserPreferences prefs = userPreferencesService.get(userId);
        return FsrsSchedulerConfig.merge(params, prefs);
    }
}
