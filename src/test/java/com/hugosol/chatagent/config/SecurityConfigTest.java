package com.hugosol.chatagent.config;

import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.UserLearningProfileRepository;
import com.hugosol.chatagent.repository.UserRepository;
import com.hugosol.chatagent.service.LearningProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private LearningProfileService learningProfileService;

    @MockBean
    private UserLearningProfileRepository userLearningProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        String encodedPassword = passwordEncoder.encode("admin123");
        User admin = new User("admin", encodedPassword);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    @Test
    void shouldRedirectUnauthenticatedToLoginPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login/main.html"));
    }

    @Test
    void shouldPermitStaticResourceUnderLoginPath() throws Exception {
        mockMvc.perform(get("/login/main.html"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRedirectRootToLoginPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login/main.html"));
    }

    @Test
    void shouldLoginSuccessfullyAndRedirect() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin123"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/index.html"));
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login/main.html?error"));
    }

    @Test
    @WithMockUser
    void shouldAccessIndexWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(authenticated());
    }

    @Test
    @WithMockUser
    void shouldAllowWebSocketPathForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/ws/chat"))
                .andExpect(status().is4xxClientError())
                .andExpect(authenticated());
    }

    @Test
    void shouldLogoutAndRedirect() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/main.html"));
    }
}
