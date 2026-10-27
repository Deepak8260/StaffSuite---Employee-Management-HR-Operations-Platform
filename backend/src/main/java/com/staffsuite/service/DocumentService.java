package com.staffsuite.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.config.AppProperties;
import com.staffsuite.dto.DocumentDtos.DocumentResponse;
import com.staffsuite.entity.DocumentCategory;
import com.staffsuite.entity.DocumentVisibility;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmployeeDocument;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.exception.ApiException;
import com.staffsuite.exception.StorageException;
import com.staffsuite.repository.DocumentCategoryRepository;
import com.staffsuite.repository.EmployeeDocumentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.util.FileTypes;

import lombok.RequiredArgsConstructor;

/**
 * Employee documents. Every access rule is enforced here, in the service layer, so it holds no matter
 * which controller or client calls it:
 * <ul>
 *   <li>HR/Admin: everything.</li>
 *   <li>The employee: their own documents that are SELF_AND_HR.</li>
 *   <li>A manager: their direct reports' documents that are SELF_AND_HR.</li>
 *   <li>HR_ONLY documents are never visible to the employee or a manager.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    public record DocumentDownload(Resource resource, String contentType, String fileName) {
    }

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String ENTITY = "EMPLOYEE_DOCUMENT";
    private static final String DIR = "employee-documents";
    private static final int MAX_EXPIRY_WINDOW_DAYS = 365;

    private final EmployeeDocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StorageService storage;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    // ------------------------------------------------------------------- upload

    @Transactional
    public DocumentResponse upload(AuthenticatedUser actor, Long employeeId, Long categoryId, LocalDate expiryDate,
            DocumentVisibility requestedVisibility, MultipartFile file, String ip) {
        boolean hr = isHrOrAdmin(actor);
        boolean self = employeeId.equals(actor.employeeId());
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            throw hr ? new ApiException(HttpStatus.NOT_FOUND, "Employee not found") : forbidden();
        }
        if (!hr && !self) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only upload your own documents");
        }
        DocumentVisibility visibility = requestedVisibility != null ? requestedVisibility : DocumentVisibility.SELF_AND_HR;
        if (!hr && visibility == DocumentVisibility.HR_ONLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only HR can restrict a document to HR");
        }
        DocumentCategory category = categoryRepository.findById(categoryId).filter(DocumentCategory::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Document category not found"));
        if (expiryDate != null && expiryDate.isAfter(LocalDate.now(clock).plusYears(50))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The expiry date is not plausible");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 5 MB)");
        }

        String extension;
        String reference;
        try {
            extension = FileTypes.detectExtension(file)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, PNG and JPEG files are allowed"));
            try (InputStream in = file.getInputStream()) {
                reference = storage.store(DIR + "/" + employeeId, UUID.randomUUID() + "." + extension, in);
            }
        } catch (IOException | StorageException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the document");
        }

        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setCategory(category);
        document.setFileName(displayName(file.getOriginalFilename(), extension));
        document.setFileUrl(reference);
        document.setContentType(FileTypes.contentTypeOf(extension));
        document.setFileSize(file.getSize());
        document.setUploadedBy(actor.userId());
        document.setExpiryDate(expiryDate);
        document.setVisibility(visibility);
        try {
            document = documentRepository.saveAndFlush(document);
        } catch (RuntimeException e) {
            removeQuietly(reference);   // do not leave an orphan file behind a failed insert
            throw e;
        }
        auditService.log(actor.userId(), "DOCUMENT_UPLOADED", ENTITY, document.getId(), ip);
        return toResponses(List.of(document)).get(0);
    }

    // --------------------------------------------------------------------- list

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(AuthenticatedUser actor, Long employeeId) {
        boolean hr = isHrOrAdmin(actor);
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            throw hr ? new ApiException(HttpStatus.NOT_FOUND, "Employee not found") : forbidden();
        }
        if (!hr && !isSelf(actor, employee) && !isDirectManager(actor, employee)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to view this employee's documents");
        }
        List<EmployeeDocument> visible = documentRepository.findByEmployee_IdOrderByCreatedAtDescIdDesc(employeeId)
                .stream().filter(d -> canSee(actor, d)).toList();
        return toResponses(visible);
    }

    // ----------------------------------------------------------------- download

    @Transactional
    public DocumentDownload download(AuthenticatedUser actor, Long id, String ip) {
        // Missing and not-allowed look the same, so guessing ids reveals nothing.
        EmployeeDocument document = documentRepository.findWithDetailsById(id).filter(d -> canSee(actor, d))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        Resource resource;
        try {
            resource = storage.load(document.getFileUrl());
        } catch (StorageException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The file for this document is missing");
        }
        auditService.log(actor.userId(), "DOCUMENT_DOWNLOADED", ENTITY, id, ip);
        return new DocumentDownload(resource, document.getContentType(), document.getFileName());
    }

    // ------------------------------------------------------------------- delete

    @Transactional
    public void delete(AuthenticatedUser actor, Long id, String ip) {
        if (!isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only HR and administrators can delete documents");
        }
        EmployeeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        String reference = document.getFileUrl();
        documentRepository.delete(document);
        auditService.log(actor.userId(), "DOCUMENT_DELETED", ENTITY, id, ip);
        // Remove the file only once the row deletion has really committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeQuietly(reference);
            }
        });
    }

    // ----------------------------------------------------------------- expiring

    /**
     * Documents expiring within the next {@code days} days (default from configuration), soonest first.
     * With {@code includeExpired}, documents that have already expired are listed too.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> expiring(AuthenticatedUser actor, Integer days, boolean includeExpired) {
        if (!isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only HR and administrators can view expiring documents");
        }
        int window = days != null ? days : props.documents().expiryWindowDays();
        if (window < 1 || window > MAX_EXPIRY_WINDOW_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a window between 1 and " + MAX_EXPIRY_WINDOW_DAYS + " days");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate to = today.plusDays(window);
        List<EmployeeDocument> found = includeExpired
                ? documentRepository.findExpiringUntil(to)
                : documentRepository.findExpiringBetween(today, to);
        return toResponses(found);
    }

    // ------------------------------------------------------------------ helpers

    private boolean canSee(AuthenticatedUser actor, EmployeeDocument document) {
        if (isHrOrAdmin(actor)) {
            return true;
        }
        if (document.getVisibility() == DocumentVisibility.HR_ONLY) {
            return false;
        }
        Employee owner = document.getEmployee();
        return isSelf(actor, owner) || isDirectManager(actor, owner);
    }

    private static boolean isHrOrAdmin(AuthenticatedUser actor) {
        return actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
    }

    private static boolean isSelf(AuthenticatedUser actor, Employee employee) {
        return employee.getId().equals(actor.employeeId());
    }

    private static boolean isDirectManager(AuthenticatedUser actor, Employee employee) {
        return actor.employeeId() != null && employee.getManager() != null
                && employee.getManager().getId().equals(actor.employeeId());
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to access these documents");
    }

    private void removeQuietly(String reference) {
        try {
            storage.delete(reference);
        } catch (StorageException e) {
            log.warn("Could not remove stored file {}: {}", reference, e.getMessage());
        }
    }

    /** Keeps only the last path segment, drops control characters and quotes, and bounds the length. */
    private static String displayName(String original, String extension) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "document." + extension;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private List<DocumentResponse> toResponses(Collection<EmployeeDocument> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<Long, String> uploaders = uploaderNames(documents);
        return documents.stream().map(d -> {
            Integer remaining = d.getExpiryDate() == null ? null
                    : (int) ChronoUnit.DAYS.between(today, d.getExpiryDate());
            Employee e = d.getEmployee();
            return new DocumentResponse(d.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(), d.getCategory().getId(),
                    d.getCategory().getName(), d.getFileName(), d.getContentType(), d.getFileSize(), d.getExpiryDate(),
                    remaining, remaining != null && remaining < 0, d.getVisibility(),
                    d.getUploadedBy() == null ? null : uploaders.get(d.getUploadedBy()), d.getCreatedAt());
        }).toList();
    }

    /** Uploader display name: their employee name if they have one, otherwise their login email. */
    private Map<Long, String> uploaderNames(Collection<EmployeeDocument> documents) {
        List<Long> userIds = documents.stream().map(EmployeeDocument::getUploadedBy).filter(java.util.Objects::nonNull)
                .distinct().toList();
        List<User> users = userRepository.findAllById(userIds);
        Map<Long, String> employeeNames = employeeRepository
                .findAllById(users.stream().map(User::getEmployeeId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
        Map<Long, String> names = new HashMap<>();
        for (User u : users) {
            names.put(u.getId(), u.getEmployeeId() != null && employeeNames.containsKey(u.getEmployeeId())
                    ? employeeNames.get(u.getEmployeeId()) : u.getEmail());
        }
        return names;
    }
}
