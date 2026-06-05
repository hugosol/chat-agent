package com.hugosol.chatagent.config;

import com.hugosol.chatagent.repository.UserRepository;
import com.hugosol.chatagent.service.SessionCleanupLogoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final SessionCleanupLogoutHandler sessionCleanupLogoutHandler;
    private final AppProperties appProperties;

    public SecurityConfig(SessionCleanupLogoutHandler sessionCleanupLogoutHandler,
                          AppProperties appProperties) {
        this.sessionCleanupLogoutHandler = sessionCleanupLogoutHandler;
        this.appProperties = appProperties;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                    .frameOptions(frameOptions -> frameOptions.sameOrigin())
            )
            .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    appProperties.getSecurity().getPermitAllPaths()
                            .forEach(path -> auth.requestMatchers(path).permitAll());
                    auth.anyRequest().authenticated();
            })
            .formLogin(form -> form
                    .loginPage("/login/main.html")
                    .loginProcessingUrl("/login")
                    .defaultSuccessUrl("/index.html")
                    .failureUrl("/login/main.html?error")
                    .permitAll()
            )
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login/main.html")
                    .addLogoutHandler(sessionCleanupLogoutHandler)
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            )
            .rememberMe(remember -> remember
                    .key(UUID.randomUUID().toString())
                    .tokenValiditySeconds(1209600)
            )
            .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/ws/chat/**", "/h2-console/**", "/logout", "/login", "/api/**")
            );
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        .password(user.getPassword())
                        .roles("admin".equals(user.getUsername()) ? "ADMIN" : "USER")
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @EventListener
    void onLoginSuccess(AuthenticationSuccessEvent event) {
        log.info("LOGIN SUCCESS: user={}", event.getAuthentication().getName());
    }

    @EventListener
    void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("LOGIN FAILURE: user={}, reason={}",
                event.getAuthentication().getName(),
                event.getException().getMessage());
    }
}
