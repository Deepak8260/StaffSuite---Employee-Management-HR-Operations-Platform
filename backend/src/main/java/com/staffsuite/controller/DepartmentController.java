package com.staffsuite.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.OrganizationDtos.DepartmentRequest;
import com.staffsuite.dto.OrganizationDtos.DepartmentResponse;
import com.staffsuite.entity.Role;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.DepartmentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private static final String HR_OR_ADMIN = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')";

    private final DepartmentService departmentService;

    /** Departments are a small bounded set, returned whole. Inactive ones are only shown to Admin/HR on request. */
    @GetMapping
    public List<DepartmentResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        boolean privileged = actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
        return departmentService.list(includeInactive && privileged);
    }

    @PostMapping
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<DepartmentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DepartmentRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.create(request, actor, http.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public DepartmentResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request, HttpServletRequest http) {
        return departmentService.update(id, request, actor, http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        departmentService.delete(id, actor, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
