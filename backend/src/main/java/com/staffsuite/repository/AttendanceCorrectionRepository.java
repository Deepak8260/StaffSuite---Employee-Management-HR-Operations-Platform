package com.staffsuite.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.AttendanceCorrection;
import com.staffsuite.entity.CorrectionStatus;

import jakarta.persistence.LockModeType;

public interface AttendanceCorrectionRepository
        extends JpaRepository<AttendanceCorrection, Long>, JpaSpecificationExecutor<AttendanceCorrection> {

    boolean existsByAttendanceRecordIdAndStatus(Long attendanceRecordId, CorrectionStatus status);

    @Query("select c.attendanceRecord.id from AttendanceCorrection c "
            + "where c.attendanceRecord.id in :recordIds and c.status = :status")
    List<Long> recordIdsWithStatus(@Param("recordIds") Collection<Long> recordIds,
            @Param("status") CorrectionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AttendanceCorrection c where c.id = :id")
    Optional<AttendanceCorrection> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = { "attendanceRecord", "requestedBy", "reviewedBy" })
    Page<AttendanceCorrection> findAll(Specification<AttendanceCorrection> spec, Pageable pageable);
}
