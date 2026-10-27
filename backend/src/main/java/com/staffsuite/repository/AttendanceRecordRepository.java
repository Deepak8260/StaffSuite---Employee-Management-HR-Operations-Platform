package com.staffsuite.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.AttendanceRecord;

import jakarta.persistence.LockModeType;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<AttendanceRecord> findByEmployeeIdAndAttendanceDateBetween(Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByEmployeeIdInAndAttendanceDateBetween(Collection<Long> employeeIds, LocalDate from,
            LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceRecord r where r.id = :id")
    Optional<AttendanceRecord> findByIdForUpdate(@Param("id") Long id);
}
