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

    public static Specification<AttendanceCorrection> withStatus(CorrectionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
