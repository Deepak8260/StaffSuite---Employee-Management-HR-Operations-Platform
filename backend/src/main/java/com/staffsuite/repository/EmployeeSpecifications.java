package com.staffsuite.repository;

import org.springframework.data.jpa.domain.Specification;

import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;

public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Employee> inDepartment(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    public static Specification<Employee> withStatus(EmploymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("employmentStatus"), status);
    }

    public static Specification<Employee> withId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Employee> managedBy(Long managerId) {
        return (root, query, cb) -> cb.equal(root.get("manager").get("id"), managerId);
    }

    /** Case-insensitive match on name or employee code. */
    public static Specification<Employee> matches(String text) {
        String escaped = text.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                cb.like(cb.lower(root.get("employeeCode")), pattern, '\\'));
    }
}
