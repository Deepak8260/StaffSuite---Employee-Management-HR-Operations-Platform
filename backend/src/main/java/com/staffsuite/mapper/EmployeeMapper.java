package com.staffsuite.mapper;

import org.springframework.stereotype.Component;

import com.staffsuite.dto.EmployeeDtos.DirectoryEntry;
import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.entity.Employee;

/** Must be called inside a transaction: department, designation and manager are lazy. */
@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFullName(),
                e.getEmail(),
                e.getPhone(),
                e.getAddress(),
                e.getEmergencyContact(),
                e.getDateOfBirth(),
                e.getJoiningDate(),
                e.getJobTitle(),
                e.getEmploymentStatus(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getDesignation() == null ? null : e.getDesignation().getId(),
                e.getDesignation() == null ? null : e.getDesignation().getTitle(),
                e.getManager() == null ? null : e.getManager().getId(),
                e.getManager() == null ? null : e.getManager().getFullName(),
                e.isDeleted(),
                photoPath(e),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    /** The photo is served by the API (it needs the login token), so the "url" is an API path, not a file location. */
    public static String photoPath(Employee e) {
        return e.getProfilePhotoUrl() == null ? null : "/api/v1/employees/" + e.getId() + "/photo";
    }

    public DirectoryEntry toDirectoryEntry(Employee e) {
        return new DirectoryEntry(e.getId(), e.getFullName(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getName());
    }
}
