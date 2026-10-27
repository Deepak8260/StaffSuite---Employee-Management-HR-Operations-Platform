package com.staffsuite.dto;

import com.staffsuite.entity.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role,
            Long employeeId) {
    }

    public record UpdateRoleRequest(@NotNull Role role) {
    }

    public record UpdateStatusRequest(@NotNull Boolean active) {
    }
}
