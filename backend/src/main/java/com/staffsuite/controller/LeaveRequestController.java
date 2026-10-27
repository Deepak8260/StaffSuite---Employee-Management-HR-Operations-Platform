package com.staffsuite.controller;

import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.LeaveDtos.AttachmentResponse;
import com.staffsuite.dto.LeaveDtos.CalendarResponse;
import com.staffsuite.dto.LeaveDtos.DecisionRequest;
import com.staffsuite.dto.LeaveDtos.LeaveApplyRequest;
import com.staffsuite.dto.LeaveDtos.LeaveRequestResponse;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.LeaveRequestService;
import com.staffsuite.service.LeaveRequestService.AttachmentDownload;
import com.staffsuite.service.LeaveRequestService.Scope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LeaveRequestController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String APPROVERS = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','MANAGER')";

    private final LeaveRequestService leaveRequestService;

    @PostMapping("/leave-requests")
    public ResponseEntity<LeaveRequestResponse> apply(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody LeaveApplyRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveRequestService.apply(actor, request, http.getRemoteAddr()));
    }

    /**
     * Role-scoped: employees see their own, managers their team's, HR/Admin everything. The default scope is
     * chosen from the caller's role; an explicit {@code scope} can only narrow it, never widen it.
     */
    @GetMapping("/leave-requests")
    public PageResponse<LeaveRequestResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Scope scope,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return leaveRequestService.list(actor, scope, status, employeeId, from, to, pageable);
    }

    @PutMapping("/leave-requests/{id}/approve")
    @PreAuthorize(APPROVERS)
    public LeaveRequestResponse approve(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body, HttpServletRequest http) {
        return leaveRequestService.approve(actor, id, body == null ? null : body.comment(), http.getRemoteAddr());
    }

    @PutMapping("/leave-requests/{id}/reject")
    @PreAuthorize(APPROVERS)
    public LeaveRequestResponse reject(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body, HttpServletRequest http) {
        return leaveRequestService.reject(actor, id, body == null ? null : body.comment(), http.getRemoteAddr());
    }

    @PutMapping("/leave-requests/{id}/cancel")
    public LeaveRequestResponse cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        return leaveRequestService.cancel(actor, id, http.getRemoteAddr());
    }

    /** Upload first, then pass the returned path as attachmentUrl when applying. */
    @PostMapping(value = "/leave-requests/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestPart("file") MultipartFile file) {
        return leaveRequestService.uploadAttachment(actor, file);
    }

    @GetMapping("/leave-requests/{id}/attachment")
    public ResponseEntity<Resource> attachment(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id) {
        AttachmentDownload file = leaveRequestService.attachment(actor, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    /** Approved leave and public holidays between two dates, limited to the caller's scope. */
    @GetMapping("/leave-calendar")
    public CalendarResponse calendar(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Scope scope,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return leaveRequestService.calendar(actor, scope, from, to);
    }
}
