package com.staffsuite.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.exception.StorageException;
import com.staffsuite.mapper.EmployeeMapper;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.util.FileTypes;

import lombok.RequiredArgsConstructor;

/** Profile photos: the image goes to file storage, the employee row keeps only the reference. */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String DIR = "employee-photos";

    public record Photo(Resource resource, String contentType) {
    }

    private final EmployeeRepository employeeRepository;
    private final StorageService storage;
    private final AuditService auditService;
    private final EmployeeMapper mapper;
    private final ProfileCompletionService profileCompletion;

    /** HR/Admin or the employee themself; JPEG and PNG only, judged from the file's own bytes. */
    @Transactional
    public EmployeeResponse upload(AuthenticatedUser actor, Long employeeId, MultipartFile file, String ip) {
        if (!isHrOrAdmin(actor) && !employeeId.equals(actor.employeeId())) {
            throw new AccessDeniedException("You can only change your own photo");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (employee.isDeleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee has been offboarded");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an image to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image is too large (maximum 5 MB)");
        }
        String reference;
        try {
            String extension = FileTypes.detectExtension(file).filter(x -> x.equals("jpg") || x.equals("png"))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Only JPG and PNG images are allowed"));
            try (InputStream in = file.getInputStream()) {
                reference = storage.store(DIR + "/" + employeeId, UUID.randomUUID() + "." + extension, in);
            }
        } catch (IOException | StorageException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the image");
        }
        String previous = employee.getProfilePhotoUrl();
        employee.setProfilePhotoUrl(reference);
        employeeRepository.saveAndFlush(employee);
        if (previous != null) {
            try {
                storage.delete(previous);
            } catch (StorageException ignored) {
                // an orphaned old file is harmless; the new photo is already in place
            }
        }
        if (employeeId.equals(actor.employeeId())) {
            profileCompletion.afterOwnUpdate(employee);   // HR changing someone's photo is not the employee completing it
        }
        auditService.log(actor.userId(), "EMPLOYEE_PHOTO_UPDATED", "EMPLOYEE", employeeId, ip);
        return mapper.toResponse(employee);
    }

    /** HR/Admin or the employee themself. Removing a photo that is not there is not an error. */
    @Transactional
    public EmployeeResponse remove(AuthenticatedUser actor, Long employeeId, String ip) {
        if (!isHrOrAdmin(actor) && !employeeId.equals(actor.employeeId())) {
            throw new AccessDeniedException("You can only change your own photo");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
        String previous = employee.getProfilePhotoUrl();
        if (previous != null) {
            employee.setProfilePhotoUrl(null);
            employeeRepository.saveAndFlush(employee);
            try {
                storage.delete(previous);
            } catch (StorageException ignored) {
                // the reference is already gone; a leftover file is harmless
            }
            auditService.log(actor.userId(), "EMPLOYEE_PHOTO_REMOVED", "EMPLOYEE", employeeId, ip);
        }
        return mapper.toResponse(employee);
    }

    /** Same audience as the profile: HR/Admin, the employee and their direct manager. */
    @Transactional(readOnly = true)
    public Photo load(AuthenticatedUser actor, Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> !e.isDeleted() || isHrOrAdmin(actor))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found"));
        boolean self = employeeId.equals(actor.employeeId());
        boolean directManager = employee.getManager() != null && employee.getManager().getId().equals(actor.employeeId());
        if (!isHrOrAdmin(actor) && !self && !directManager) {
            throw new AccessDeniedException("Access denied");
        }
        String reference = employee.getProfilePhotoUrl();
        if (reference == null || !storage.exists(reference)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        String extension = reference.substring(reference.lastIndexOf('.') + 1);
        return new Photo(storage.load(reference), FileTypes.contentTypeOf(extension));
    }

    private static boolean isHrOrAdmin(AuthenticatedUser actor) {
        return actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
    }
}
