package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.UserPreferencesRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Test
    void get_returnsExistingPreferences() {
        var service = new UserPreferencesService(preferencesRepository);
        UserPreferences prefs = new UserPreferences("user-1");
        prefs.setNewCardDailyLimit(15);
        prefs.setDayStartHour(8);

        when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.of(prefs));

        UserPreferences result = service.get("user-1");

        assertThat(result.getNewCardDailyLimit()).isEqualTo(15);
        assertThat(result.getDayStartHour()).isEqualTo(8);
    }

    @Test
    void get_createsDefaultsWhenNotFound() {
        var service = new UserPreferencesService(preferencesRepository);

        when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(preferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferences result = service.get("user-1");

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getNewCardDailyLimit()).isEqualTo(20);
        assertThat(result.getDayStartHour()).isEqualTo(6);

        verify(preferencesRepository).save(any(UserPreferences.class));
    }

    @Test
    void save_updatesPreferences() {
        var service = new UserPreferencesService(preferencesRepository);
        UserPreferences prefs = new UserPreferences("user-1");
        prefs.setNewCardDailyLimit(30);
        prefs.setDayStartHour(4);
        prefs.setTimezone("America/New_York");
        prefs.setLastDeckId("deck-1");
        prefs.setLastMode("STANDARD");

        when(preferencesRepository.save(prefs)).thenReturn(prefs);

        UserPreferences result = service.save(prefs);

        assertThat(result.getNewCardDailyLimit()).isEqualTo(30);
        assertThat(result.getTimezone()).isEqualTo("America/New_York");
        verify(preferencesRepository).save(prefs);
    }
}
