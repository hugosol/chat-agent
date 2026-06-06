package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.AdminResetPasswordRequest;
import com.hugosol.chatagent.dto.ChangePasswordRequest;
import com.hugosol.chatagent.dto.CreateUserRequest;
import com.hugosol.chatagent.dto.ToggleEnabledRequest;
import com.hugosol.chatagent.dto.UserListItem;
import com.hugosol.chatagent.dto.UserMeResponse;
import com.hugosol.chatagent.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/me")
    public ResponseEntity<UserMeResponse> getMe(Principal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal));
    }

    @PutMapping("/user/password")
    public ResponseEntity<Void> changePassword(Principal principal,
                                               @RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(principal, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/admin/users")
    public ResponseEntity<List<UserListItem>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @PostMapping("/admin/users")
    public ResponseEntity<UserListItem> createUser(@RequestBody CreateUserRequest request) {
        try {
            return ResponseEntity.ok(userService.createUser(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/admin/users/{id}/enabled")
    public ResponseEntity<Void> toggleEnabled(Principal principal,
                                              @PathVariable String id,
                                              @RequestBody ToggleEnabledRequest request) {
        try {
            userService.toggleEnabled(principal, id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/admin/users/{id}/password")
    public ResponseEntity<Void> adminResetPassword(Principal principal,
                                                   @PathVariable String id,
                                                   @RequestBody AdminResetPasswordRequest request) {
        try {
            userService.adminResetPassword(principal, id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
