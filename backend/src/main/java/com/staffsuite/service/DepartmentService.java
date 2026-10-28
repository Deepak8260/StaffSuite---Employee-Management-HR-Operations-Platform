package com.staffsuite.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.OrganizationDtos.DepartmentRequest;
import com.staffsuite.dto.OrganizationDtos.DepartmentResponse;
import com.staffsuite.entity.Department;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private static final String ENTITY = "DEPARTMENT";

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DesignationRepository designationRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list(boolean includeInactive) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : employeeRepository.countActiveByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        List<Department> departments = includeInactive
                ? departmentRepository.findAllByOrderByNameAsc()
                : departmentRepository.findByActiveTrueOrderByNameAsc();
        return departments.stream().map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L))).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request, AuthenticatedUser actor, String ip) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A department named '" + name + "' already exists");
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription(blankToNull(request.description()));
        department = departmentRepository.save(department);
        auditService.log(actor.userId(), "DEPARTMENT_CREATED", ENTITY, department.getId(), ip);
        return toResponse(department, 0);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request, AuthenticatedUser actor, String ip) {
        Department department = find(id);
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A department named '" + name + "' already exists");
        }
        if (Boolean.FALSE.equals(request.active()) && department.isActive()) {
            assertNoActiveEmployees(department);
        }
        department.setName(name);
        department.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            department.setActive(request.active());
        }
        auditService.log(actor.userId(), "DEPARTMENT_UPDATED", ENTITY, id, ip);
        return toResponse(department, employeeRepository.countByDepartment_IdAndDeletedFalse(id));
    }

    /**
     * Soft delete. Offboarded employees keep pointing at the department for history, so the row
     * is deactivated rather than removed. Blocked while active employees are still assigned.
     */
    @Transactional
    public void delete(Long id, AuthenticatedUser actor, String ip) {
        Department department = find(id);
        if (!department.isActive()) {
            return;
        }
        assertNoActiveEmployees(department);
        assertNoActiveDesignations(department);
        department.setActive(false);
        auditService.log(actor.userId(), "DEPARTMENT_DELETED", ENTITY, id, ip);
    }

    private void assertNoActiveEmployees(Department department) {
        long active = employeeRepository.countByDepartment_IdAndDeletedFalse(department.getId());
        if (active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete department '" + department.getName() + "': "
                    + active + " active employee" + (active == 1 ? " is" : "s are")
                    + " still assigned to it. Reassign them to another department first.");
        }
    }

    private void assertNoActiveDesignations(Department department) {
        long active = designationRepository.countByDepartmentIdAndActiveTrue(department.getId());
        if (active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete department '" + department.getName() + "': "
                    + active + " active designation" + (active == 1 ? " belongs" : "s belong")
                    + " to it. Move or deactivate them first.");
        }
    }

    private Department find(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
    }

    private static DepartmentResponse toResponse(Department d, long employeeCount) {
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.isActive(), employeeCount);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
