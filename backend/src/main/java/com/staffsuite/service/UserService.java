package com.staffsuite.service;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.staffsuite.dto.PageResponse;
import com.staffsuite.dto.UserDtos.CreateUserRequest;
import com.staffsuite.dto.UserResponse;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), UserResponse::from);
    }

    public UserResponse create(CreateUserRequest request, AuthenticatedUser actor, String ip) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEmployeeId(request.employeeId());
        User saved = userRepository.save(user);
        auditService.log(actor.userId(), "USER_CREATED", "USER", saved.getId(), ip);
        return UserResponse.from(saved);
    }

    public UserResponse updateRole(Long id, Role role, AuthenticatedUser actor, String ip) {
        if (id.equals(actor.userId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        }
        User user = find(id);
        user.setRole(role);
        User saved = userRepository.save(user);
        auditService.log(actor.userId(), "USER_ROLE_CHANGED", "USER", id, ip);
        return UserResponse.from(saved);
    }

    public UserResponse updateStatus(Long id, boolean active, AuthenticatedUser actor, String ip) {
        if (id.equals(actor.userId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot change your own status");
        }
        User user = find(id);
        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.log(actor.userId(), active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", id, ip);
        return UserResponse.from(saved);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
