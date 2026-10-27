package com.staffsuite.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.OrganizationDtos.DesignationRequest;
import com.staffsuite.dto.OrganizationDtos.DesignationResponse;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Designation;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private static final String ENTITY = "DESIGNATION";

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DesignationResponse> list(Long departmentId, boolean includeInactive) {
        Map<Long, String> departmentNames = new HashMap<>();
        departmentRepository.findAll().forEach(d -> departmentNames.put(d.getId(), d.getName()));
        List<Designation> designations = includeInactive
                ? designationRepository.findAllByOrderByTitleAsc()
                : designationRepository.findByActiveTrueOrderByTitleAsc();
        return designations.stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .map(d -> toResponse(d, departmentNames.get(d.getDepartmentId())))
                .toList();
    }

    @Transactional
    public DesignationResponse create(DesignationRequest request, AuthenticatedUser actor, String ip) {
        String title = request.title().trim();
        Department department = resolveDepartment(request.departmentId());
        assertTitleFree(title, request.departmentId(), null);
        Designation designation = new Designation();
        designation.setTitle(title);
        designation.setDescription(blankToNull(request.description()));
        designation.setDepartmentId(request.departmentId());
        designation = designationRepository.save(designation);
        auditService.log(actor.userId(), "DESIGNATION_CREATED", ENTITY, designation.getId(), ip);
        return toResponse(designation, department == null ? null : department.getName());
    }

    @Transactional
    public DesignationResponse update(Long id, DesignationRequest request, AuthenticatedUser actor, String ip) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Designation not found"));
        String title = request.title().trim();
        Department department = resolveDepartment(request.departmentId());
        assertTitleFree(title, request.departmentId(), id);
        designation.setTitle(title);
        designation.setDescription(blankToNull(request.description()));
        designation.setDepartmentId(request.departmentId());
        if (request.active() != null) {
            designation.setActive(request.active());
        }
        auditService.log(actor.userId(), "DESIGNATION_UPDATED", ENTITY, id, ip);
        return toResponse(designation, department == null ? null : department.getName());
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId).filter(Department::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Department not found"));
    }

    private void assertTitleFree(String title, Long departmentId, Long excludeId) {
        if (designationRepository.titleTaken(title, departmentId, excludeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A designation titled '" + title
                    + "' already exists" + (departmentId == null ? " without a department" : " in this department"));
        }
    }

    private static DesignationResponse toResponse(Designation d, String departmentName) {
        return new DesignationResponse(d.getId(), d.getTitle(), d.getDescription(), d.getDepartmentId(),
                departmentName, d.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
