package com.staffsuite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.LeaveBalance;

import jakarta.persistence.LockModeType;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(Long employeeId, int year);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);

    /** Row-locking read used whenever days are consumed or restored, so concurrent decisions serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.employeeId = :employeeId and b.leaveTypeId = :leaveTypeId "
            + "and b.year = :year")
    Optional<LeaveBalance> findForUpdate(@Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);
}
