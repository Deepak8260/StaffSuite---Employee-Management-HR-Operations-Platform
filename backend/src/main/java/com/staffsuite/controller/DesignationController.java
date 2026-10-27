package com.staffsuite.controller;

import java.util.List;

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

import com.staffsuite.dto.OrganizationDtos.DesignationRequest;
import com.staffsuite.dto.OrganizationDtos.DesignationResponse;
import com.staffsuite.entity.Role;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.DesignationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/designations")
@RequiredArgsConstructor
public class DesignationController {

    private static final String HR_OR_ADMIN = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')";

    private final DesignationService designationService;

    @GetMapping
    public List<DesignationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        boolean privileged = actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
        return designationService.list(departmentId, includeInactive && privileged);
    }

    @PostMapping
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<DesignationResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DesignationRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(designationService.create(request, actor, http.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public DesignationResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody DesignationRequest request, HttpServletRequest http) {
        return designationService.update(id, request, actor, http.getRemoteAddr());
    }
}
