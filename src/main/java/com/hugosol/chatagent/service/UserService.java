package com.hugosol.chatagent.service;

import com.hugosol.chatagent.dto.AdminResetPasswordRequest;
import com.hugosol.chatagent.dto.ChangePasswordRequest;
import com.hugosol.chatagent.dto.CreateUserRequest;
import com.hugosol.chatagent.dto.ToggleEnabledRequest;
import com.hugosol.chatagent.dto.UserListItem;
import com.hugosol.chatagent.dto.UserMeResponse;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserMeResponse getCurrentUser(Principal principal) {
        if (principal == null) {
            return new UserMeResponse("anonymous", false);
        }
        String username = principal.getName();
        boolean admin = "admin".equals(username);
        return new UserMeResponse(username, admin);
    }

    public void changePassword(Principal principal, ChangePasswordRequest request) {
        User user = findUser(principal.getName());
        verifyCurrentPassword(user, request.currentPassword());
        validatePassword(request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public List<UserListItem> listUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .sorted(Comparator.comparing(u -> u.getCreateTime()))
                .map(u -> new UserListItem(u.getId(), u.getUsername(), u.getCreateTime(), u.isEnabled()))
                .toList();
    }

    public UserListItem createUser(CreateUserRequest request) {
        validatePassword(request.password());
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        return new UserListItem(user.getId(), user.getUsername(), user.getCreateTime(), user.isEnabled());
    }

    public void toggleEnabled(Principal principal, String userId, ToggleEnabledRequest request) {
        User target = findUserById(userId);
        if ("admin".equals(target.getUsername())) {
            throw new IllegalArgumentException("Cannot disable admin user");
        }
        if (principal.getName().equals(target.getUsername())) {
            throw new IllegalArgumentException("Cannot disable yourself");
        }
        target.setEnabled(request.enabled());
        userRepository.save(target);
    }

    public void adminResetPassword(Principal principal, String userId, AdminResetPasswordRequest request) {
        User adminUser = findUser(principal.getName());
        verifyCurrentPassword(adminUser, request.adminPassword());

        User target = findUserById(userId);
        if ("admin".equals(target.getUsername())) {
            throw new IllegalArgumentException("Cannot reset admin password");
        }
        validatePassword(request.newPassword());
        target.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(target);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private User findUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void verifyCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }
}
