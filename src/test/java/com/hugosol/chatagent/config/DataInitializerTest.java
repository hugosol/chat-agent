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

        dataInitializer.run();

        ArgumentCaptor<AssertionGroup> captor = ArgumentCaptor.forClass(AssertionGroup.class);
        verify(assertionGroupRepository).save(captor.capture());
        AssertionGroup saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("error-pattern");
        assertThat(saved.getDescription()).contains("Grammar");
    }

    @Test
    void shouldNotDuplicateAssertionGroup() throws Exception {
        when(appProperties.getInitialUsers()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());
        when(assertionGroupRepository.findByName("error-pattern"))
                .thenReturn(Optional.of(new AssertionGroup("error-pattern", "desc")));

        dataInitializer.run();

        verify(assertionGroupRepository, never()).save(any(AssertionGroup.class));
    }
}
