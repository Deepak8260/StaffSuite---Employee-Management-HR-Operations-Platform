package com.staffsuite.repository;

import org.springframework.data.jpa.domain.Specification;

import com.staffsuite.entity.AttendanceCorrection;
import com.staffsuite.entity.CorrectionStatus;

public final class AttendanceCorrectionSpecifications {

    private AttendanceCorrectionSpecifications() {
    }

    public static Specification<AttendanceCorrection> requestedBy(Long employeeId) {
        return (root, query, cb) -> cb.equal(root.get("requestedBy").get("id"), employeeId);
    }

    /** Corrections requested by employees whose direct manager is the given employee. */
    public static Specification<AttendanceCorrection> ofTeamOf(Long managerEmployeeId) {
        return (root, query, cb) -> cb.equal(root.get("requestedBy").get("manager").get("id"), managerEmployeeId);
    }

    /** Everyone's corrections except one employee's own (nobody decides their own). */
    public static Specification<AttendanceCorrection> notRequestedBy(Long employeeId) {
        return (root, query, cb) -> cb.notEqual(root.get("requestedBy").get("id"), employeeId);
    }

    public static Specification<AttendanceCorrection> withStatus(CorrectionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
