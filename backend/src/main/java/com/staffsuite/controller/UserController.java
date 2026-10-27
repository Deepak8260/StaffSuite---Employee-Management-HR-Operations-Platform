package com.staffsuite.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.PageResponse;
import com.staffsuite.dto.UserDtos.CreateUserRequest;
import com.staffsuite.dto.UserDtos.UpdateRoleRequest;
import com.staffsuite.dto.UserDtos.UpdateStatusRequest;
import com.staffsuite.dto.UserResponse;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id"));
        return userService.list(pageable);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, actor, http.getRemoteAddr()));
    }

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return userService.updateRole(id, request.role(), actor, http.getRemoteAddr());
    }

    @PutMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return userService.updateStatus(id, request.active(), actor, http.getRemoteAddr());
    }
}
