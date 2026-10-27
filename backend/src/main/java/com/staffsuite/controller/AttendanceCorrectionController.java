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

import com.staffsuite.dto.AttendanceDtos.CorrectionRequest;
import com.staffsuite.dto.AttendanceDtos.CorrectionResponse;
import com.staffsuite.dto.LeaveDtos.DecisionRequest;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.CorrectionStatus;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.AttendanceCorrectionService;
import com.staffsuite.service.AttendanceCorrectionService.Scope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/attendance-corrections")
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String APPROVERS = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','MANAGER')";

    private final AttendanceCorrectionService correctionService;

    @PostMapping
    public ResponseEntity<CorrectionResponse> submit(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CorrectionRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(correctionService.submit(actor, request, http.getRemoteAddr()));
    }

    /**
     * Role-scoped: employees see their own, managers their team's, HR/Admin everything. An explicit
     * {@code scope} can only narrow the default, never widen it.
     */
    @GetMapping
    public PageResponse<CorrectionResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Scope scope,
            @RequestParam(required = false) CorrectionStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return correctionService.list(actor, scope, status, pageable);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize(APPROVERS)
    public CorrectionResponse approve(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body, HttpServletRequest http) {
        return correctionService.approve(actor, id, body == null ? null : body.comment(), http.getRemoteAddr());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize(APPROVERS)
    public CorrectionResponse reject(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body, HttpServletRequest http) {
        return correctionService.reject(actor, id, body == null ? null : body.comment(), http.getRemoteAddr());
    }
}
