package com.staffsuite.dto;

import java.util.List;

import com.staffsuite.entity.EmploymentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    /** {@code active} is optional: omit to leave unchanged, send true to restore a deleted department. */
    public record DepartmentRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            Boolean active) {
    }

    public record DepartmentResponse(Long id, String name, String description, boolean active, long employeeCount) {
    }

    public record DesignationRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            Long departmentId,
            Boolean active) {
    }

    public record DesignationResponse(Long id, String title, String description, Long departmentId,
            String departmentName, boolean active) {
    }

    /**
     * Change an employee's department and/or manager. Send only what should change.
     * Use {@code removeManager = true} (with no managerId) to clear the manager.
     */
    public record ReassignRequest(Long departmentId, Long managerId, Boolean removeManager) {

        public boolean shouldRemoveManager() {
            return Boolean.TRUE.equals(removeManager);
        }
    }

    public record OrgNode(Long id, String employeeCode, String fullName, String jobTitle, String departmentName,
            EmploymentStatus employmentStatus, List<OrgNode> children) {
    }
}
