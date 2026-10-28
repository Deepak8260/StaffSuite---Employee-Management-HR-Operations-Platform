package com.staffsuite.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;

public final class LeaveRequestSpecifications {

    private LeaveRequestSpecifications() {
    }

    public static Specification<LeaveRequest> ofEmployee(Long employeeId) {
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    /** Requests from employees whose direct manager is the given employee. */
    public static Specification<LeaveRequest> ofTeamOf(Long managerEmployeeId) {
        return (root, query, cb) -> cb.equal(root.get("employee").get("manager").get("id"), managerEmployeeId);
    }

    /** Everyone's requests except one employee's own (nobody decides their own leave). */
    public static Specification<LeaveRequest> notOfEmployee(Long employeeId) {
        return (root, query, cb) -> cb.notEqual(root.get("employee").get("id"), employeeId);
    }

    public static Specification<LeaveRequest> withStatus(LeaveStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Requests that end on or after the date (i.e. are not entirely before it). */
    public static Specification<LeaveRequest> endingOnOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from);
    }

    /** Requests that start on or before the date (i.e. are not entirely after it). */
    public static Specification<LeaveRequest> startingOnOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to);
    }

    /** Requests whose date range intersects [from, to]. */
    public static Specification<LeaveRequest> overlapping(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), to),
                cb.greaterThanOrEqualTo(root.get("endDate"), from));
    }
}
