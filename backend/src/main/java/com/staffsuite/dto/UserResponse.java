package com.staffsuite.dto;

import java.time.Instant;

import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;

/** Public view of a user. Deliberately has no password or reset-token fields. */
public record UserResponse(
        Long id,
        String email,
        Role role,
        boolean active,
        Long employeeId,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.isActive(), u.getEmployeeId(),
                u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
