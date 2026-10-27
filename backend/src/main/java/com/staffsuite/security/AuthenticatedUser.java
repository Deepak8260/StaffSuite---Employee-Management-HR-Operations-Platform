package com.staffsuite.security;

import com.staffsuite.entity.Role;

/** Principal placed in the SecurityContext for authenticated requests. */
public record AuthenticatedUser(Long userId, String email, Role role, Long employeeId) {
}
