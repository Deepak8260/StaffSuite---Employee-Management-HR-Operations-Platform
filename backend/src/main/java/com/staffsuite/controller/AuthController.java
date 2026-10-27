package com.staffsuite.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.AuthDtos.ChangePasswordRequest;
import com.staffsuite.dto.AuthDtos.LoginRequest;
import com.staffsuite.dto.AuthDtos.LoginResponse;
import com.staffsuite.dto.AuthDtos.MessageResponse;
import com.staffsuite.dto.AuthDtos.ResetPasswordConfirmRequest;
import com.staffsuite.dto.AuthDtos.ResetPasswordRequest;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest http) {
        authService.logout(principal, http.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        authService.changePassword(principal, request.currentPassword(), request.newPassword(), http.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse("Password changed"));
    }

    @PostMapping("/reset-password-request")
    public ResponseEntity<MessageResponse> resetPasswordRequest(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest http) {
        authService.requestPasswordReset(request.email(), http.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse("If the account exists, a reset token has been issued"));
    }

    @PostMapping("/reset-password-confirm")
    public ResponseEntity<MessageResponse> resetPasswordConfirm(
            @Valid @RequestBody ResetPasswordConfirmRequest request, HttpServletRequest http) {
        authService.confirmPasswordReset(request.token(), request.newPassword(), http.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse("Password has been reset"));
    }
}
