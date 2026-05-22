package com.hugosol.webagent.config;

import com.hugosol.webagent.model.User;
import com.hugosol.webagent.repository.UserRepository;
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
    private AppProperties appProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

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
}
