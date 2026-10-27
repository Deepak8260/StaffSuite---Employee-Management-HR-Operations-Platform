package com.staffsuite.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.DocumentDtos.DocumentResponse;
import com.staffsuite.entity.DocumentVisibility;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.DocumentService;
import com.staffsuite.service.DocumentService.DocumentDownload;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private static final String HR_OR_ADMIN = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')";

    private final DocumentService documentService;

    /** Self (own documents) or HR/Admin (anyone). All access rules are enforced in the service. */
    @PostMapping(value = "/employees/{employeeId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long employeeId,
            @RequestPart("file") MultipartFile file,
            @RequestParam Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) DocumentVisibility visibility,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                documentService.upload(actor, employeeId, categoryId, expiryDate, visibility, file, http.getRemoteAddr()));
    }

    @GetMapping("/employees/{employeeId}/documents")
    public List<DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long employeeId) {
        return documentService.list(actor, employeeId);
    }

    /** Streams the file through an access check; there is no way to fetch a stored file by path. */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        DocumentDownload file = documentService.download(actor, id, http.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        documentService.delete(actor, id, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Documents expiring within the window (default from configuration, or {@code days}); HR/Admin only. */
    @GetMapping("/documents/expiring")
    @PreAuthorize(HR_OR_ADMIN)
    public List<DocumentResponse> expiring(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "false") boolean includeExpired) {
        return documentService.expiring(actor, days, includeExpired);
    }
}
