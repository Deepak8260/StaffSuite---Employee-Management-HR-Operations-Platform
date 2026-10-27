package com.staffsuite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInMs, UserResponse user) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record ResetPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
