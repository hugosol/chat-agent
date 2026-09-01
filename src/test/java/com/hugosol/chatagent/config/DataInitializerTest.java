package com.hugosol.chatagent.config;

import com.hugosol.chatagent.model.AssertionGroup;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.AssertionGroupRepository;
import com.hugosol.chatagent.repository.UserRepository;
import com.hugosol.chatagent.service.FsrsParametersService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FsrsParametersService fsrsParametersService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AssertionGroupRepository assertionGroupRepository;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    void shouldCreateUserWhenNotExists() throws Exception {
        var initialUser = new AppProperties.InitialUser("admin", "admin123");
        when(appProperties.getInitialUsers()).thenReturn(List.of(initialUser));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("$2a$10$hashed");

        dataInitializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void shouldSkipWhenUserAlreadyExists() throws Exception {
        var initialUser = new AppProperties.InitialUser("admin", "admin123");
        when(appProperties.getInitialUsers()).thenReturn(List.of(initialUser));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "existing-hash")));

        dataInitializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldHandleEmptyInitialUsersList() throws Exception {
        when(appProperties.getInitialUsers()).thenReturn(List.of());

        dataInitializer.run();

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldCreateFsrsParametersForNewUser() throws Exception {
        User existingUser = new User("admin", "existing-hash");
        existingUser.setId("user-1");
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(existingUser));
        when(fsrsParametersService.get("user-1")).thenReturn(null);

        dataInitializer.run();

        ArgumentCaptor<FsrsParameters> captor = ArgumentCaptor.forClass(FsrsParameters.class);
        verify(fsrsParametersService).save(captor.capture());
        FsrsParameters saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getWeights()).hasSize(21);
    }

    @Test
    void shouldNotDuplicateFsrsParameters() throws Exception {
        User existingUser = new User("admin", "existing-hash");
        existingUser.setId("user-1");
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(existingUser));
        when(fsrsParametersService.get("user-1"))
                .thenReturn(FsrsParameters.defaults("user-1"));

        dataInitializer.run();

        verify(fsrsParametersService, never()).save(any(FsrsParameters.class));
    }

    @Test
    void shouldCreateAssertionGroupWhenNotExists() throws Exception {
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());
        when(assertionGroupRepository.findByName("error-pattern")).thenReturn(Optional.empty());
        when(assertionGroupRepository.findByName("dev-progress")).thenReturn(Optional.empty());

        dataInitializer.run();

        ArgumentCaptor<AssertionGroup> captor = ArgumentCaptor.forClass(AssertionGroup.class);
        verify(assertionGroupRepository, times(2)).save(captor.capture());
        List<AssertionGroup> saved = captor.getAllValues();

        AssertionGroup errorPattern = saved.get(0);
        assertThat(errorPattern.getName()).isEqualTo("error-pattern");
        assertThat(errorPattern.getDescription()).contains("Grammar");
        assertThat(errorPattern.getMode()).isEqualTo("WORKPLACE_STANDUP");

        AssertionGroup devProgress = saved.get(1);
        assertThat(devProgress.getName()).isEqualTo("dev-progress");
        assertThat(devProgress.getDescription()).contains("development");
        assertThat(devProgress.getMode()).isEqualTo("WORKPLACE_STANDUP");
    }

    @Test
    void shouldNotDuplicateAssertionGroup() throws Exception {
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());
        AssertionGroup existing = new AssertionGroup("error-pattern", "desc", "WORKPLACE_STANDUP");
        when(assertionGroupRepository.findByName("error-pattern")).thenReturn(Optional.of(existing));
        when(assertionGroupRepository.findByName("dev-progress"))
                .thenReturn(Optional.of(new AssertionGroup("dev-progress", "desc", "WORKPLACE_STANDUP")));

        dataInitializer.run();

        verify(assertionGroupRepository, never()).save(any(AssertionGroup.class));
    }

    @Test
    void shouldBackfillNullModeOnExistingErrorPattern() throws Exception {
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());
        AssertionGroup existing = new AssertionGroup("error-pattern", "desc"); // mode=null
        when(assertionGroupRepository.findByName("error-pattern")).thenReturn(Optional.of(existing));
        when(assertionGroupRepository.findByName("dev-progress"))
                .thenReturn(Optional.of(new AssertionGroup("dev-progress", "desc", "WORKPLACE_STANDUP")));

        dataInitializer.run();

        ArgumentCaptor<AssertionGroup> captor = ArgumentCaptor.forClass(AssertionGroup.class);
        verify(assertionGroupRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("WORKPLACE_STANDUP");
        assertThat(existing.getMode()).isEqualTo("WORKPLACE_STANDUP");
    }
}
