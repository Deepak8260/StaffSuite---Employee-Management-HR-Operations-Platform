package com.staffsuite.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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

import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;

import jakarta.persistence.LockModeType;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    /** True if the employee already has a request in one of the given statuses overlapping [start, end]. */
    @Query("select count(r) > 0 from LeaveRequest r where r.employee.id = :employeeId "
            + "and r.status in :statuses and r.startDate <= :end and r.endDate >= :start")
    boolean existsOverlapping(@Param("employeeId") Long employeeId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("statuses") Collection<LeaveStatus> statuses);

    /** Days already requested but not yet decided, for one leave type within a calendar year. */
    @Query("select coalesce(sum(r.totalDays), 0) from LeaveRequest r where r.employee.id = :employeeId "
            + "and r.leaveType.id = :leaveTypeId and r.status = :status "
            + "and r.startDate >= :yearStart and r.startDate <= :yearEnd")
    BigDecimal sumDays(@Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId,
            @Param("status") LeaveStatus status, @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd);

    /** Leave in the given status for any of the employees that touches [from, to]. */
    @Query("select r from LeaveRequest r join fetch r.leaveType where r.employee.id in :employeeIds "
            + "and r.status = :status and r.startDate <= :to and r.endDate >= :from")
    java.util.List<LeaveRequest> findOverlapping(@Param("employeeIds") Collection<Long> employeeIds,
            @Param("status") LeaveStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Approved leave touching [from, to] for current employees, optionally in one department. Used by the
     * utilisation report, which prorates each request to the part that falls inside the range.
     */
    @Query("select r from LeaveRequest r join fetch r.leaveType join fetch r.employee e left join e.department d "
            + "where r.status = :status and r.startDate <= :to and r.endDate >= :from and e.deleted = false "
            + "and (:departmentId is null or d.id = :departmentId)")
    java.util.List<LeaveRequest> findForUtilization(@Param("status") LeaveStatus status, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("departmentId") Long departmentId);

    @Override
    @EntityGraph(attributePaths = { "employee", "leaveType", "approver" })
    Page<LeaveRequest> findAll(Specification<LeaveRequest> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "employee", "leaveType" })
    java.util.List<LeaveRequest> findAll(Specification<LeaveRequest> spec);
}
