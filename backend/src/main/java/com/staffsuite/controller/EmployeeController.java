package com.staffsuite.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.EmployeeDtos.BulkUploadResult;
import com.staffsuite.dto.EmployeeDtos.DirectoryEntry;
import com.staffsuite.dto.EmployeeDtos.EmployeeProfile;
import com.staffsuite.dto.EmployeeDtos.EmployeeRequest;
import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.dto.EmployeeDtos.SelfProfileRequest;
import com.staffsuite.dto.OrganizationDtos.ReassignRequest;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.EmployeeBulkImportService;
import com.staffsuite.service.EmployeeExportService;
import com.staffsuite.service.EmployeeExportService.Export;
import com.staffsuite.service.EmployeePhotoService;
import com.staffsuite.service.EmployeePhotoService.Photo;
import com.staffsuite.service.EmployeeProfileService;
import com.staffsuite.service.EmployeeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final String HR_OR_ADMIN = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')";
    private static final String HR_ADMIN_OR_MANAGER = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','MANAGER')";

    private final EmployeeService employeeService;
    private final EmployeeBulkImportService bulkImportService;
    private final EmployeeProfileService profileService;
    private final EmployeeExportService exportService;
    private final EmployeePhotoService photoService;

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    /** Admin/HR see everyone; a Manager is automatically limited to their own direct reports. */
    @GetMapping
    @PreAuthorize(HR_ADMIN_OR_MANAGER)
    public PageResponse<EmployeeResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Long managerId,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeOffboarded,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return employeeService.list(actor, departmentId, status, managerId, search, includeOffboarded,
                pageable(page, size, Sort.by("employeeCode")));
    }

    /** Basic info (name, title, department) of the people the caller is allowed to see (see EmployeeService.directory). */
    @GetMapping("/directory")
    public PageResponse<DirectoryEntry> directory(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return employeeService.directory(actor, search, pageable(page, size, Sort.by("fullName")));
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return employeeService.get(actor, id);
    }

    /** Everything for the profile page in one call; directory-level viewers get a reduced version. */
    @GetMapping("/{id}/profile")
    public EmployeeProfile profile(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return profileService.profile(actor, id);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<org.springframework.core.io.Resource> photo(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id) {
        Photo photo = photoService.load(actor, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.resource());
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeResponse uploadPhoto(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        return photoService.upload(actor, id, file, http.getRemoteAddr());
    }

    @DeleteMapping("/{id}/photo")
    public EmployeeResponse removePhoto(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        return photoService.remove(actor, id, http.getRemoteAddr());
    }

    @GetMapping("/{id}/team")
    @PreAuthorize(HR_ADMIN_OR_MANAGER)
    public PageResponse<EmployeeResponse> team(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return employeeService.team(actor, id, pageable(page, size, Sort.by("employeeCode")));
    }

    @PostMapping
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<EmployeeResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody EmployeeRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.create(actor, request, http.getRemoteAddr()));
    }

    /** The list as an Excel report, with the same filters as {@link #list} but every matching row instead of one page. */
    @GetMapping("/export")
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Long managerId,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeOffboarded, HttpServletRequest http) {
        Export file = exportService.export(actor, departmentId, status, managerId, search, includeOffboarded, http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @GetMapping("/bulk-upload/template")
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<byte[]> bulkUploadTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employee_bulk_upload_template.xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bulkImportService.template());
    }

    /** Valid rows are created, invalid ones are reported with a reason; a wrong header row rejects the whole file. */
    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(HR_OR_ADMIN)
    public BulkUploadResult bulkUpload(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        return bulkImportService.importFile(actor, file, http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public EmployeeResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request, HttpServletRequest http) {
        return employeeService.update(actor, id, request, http.getRemoteAddr());
    }

    /** Self-service: phone, address and emergency contact only. */
    @PutMapping("/{id}/profile")
    public EmployeeResponse updateOwnProfile(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody SelfProfileRequest request, HttpServletRequest http) {
        return employeeService.updateOwnProfile(actor, id, request, http.getRemoteAddr());
    }

    @PutMapping("/{id}/reassign")
    @PreAuthorize(HR_OR_ADMIN)
    public EmployeeResponse reassign(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @RequestBody ReassignRequest request, HttpServletRequest http) {
        return employeeService.reassign(actor, id, request, http.getRemoteAddr());
    }

    @PutMapping("/{id}/offboard")
    @PreAuthorize(HR_OR_ADMIN)
    public EmployeeResponse offboard(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        return employeeService.offboard(actor, id, http.getRemoteAddr());
    }
}
