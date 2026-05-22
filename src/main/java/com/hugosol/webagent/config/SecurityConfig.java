package com.hugosol.webagent.config;

import com.hugosol.webagent.repository.UserRepository;
import com.hugosol.webagent.service.SessionCleanupLogoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionCleanupLogoutHandler sessionCleanupLogoutHandler;

    public SecurityConfig(SessionCleanupLogoutHandler sessionCleanupLogoutHandler) {
        this.sessionCleanupLogoutHandler = sessionCleanupLogoutHandler;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login/**").permitAll()
                    .requestMatchers("/h2-console/**").authenticated()
                    .anyRequest().authenticated()
            )
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
                    .ignoringRequestMatchers("/ws/coach/**", "/h2-console/**", "/logout")
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
