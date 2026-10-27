package com.staffsuite.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.AttendanceDtos.CorrectionRequest;
import com.staffsuite.dto.AttendanceDtos.CorrectionResponse;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.AttendanceCorrection;
import com.staffsuite.entity.AttendanceRecord;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.CorrectionStatus;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceCorrectionSpecifications;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    /** Whose corrections a listing covers. */
    public enum Scope { MINE, TEAM, ALL }

    private static final String ENTITY = "ATTENDANCE_CORRECTION";

    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceService attendanceService;
    private final AttendanceRules rules;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------- submit

    @Transactional
    public CorrectionResponse submit(AuthenticatedUser actor, CorrectionRequest request, String ip) {
        Employee employee = attendanceService.activeEmployee(actor);
        if (request.attendanceRecordId() == null && request.attendanceDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide the attendance record or the date to correct");
        }

        AttendanceRecord record;
        if (request.attendanceRecordId() != null) {
            record = recordRepository.findById(request.attendanceRecordId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance record not found"));
            if (!record.getEmployeeId().equals(employee.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only request corrections to your own attendance");
            }
        } else {
            record = recordRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), request.attendanceDate())
                    .orElse(null);
        }
        LocalDate date = record != null ? record.getAttendanceDate() : request.attendanceDate();

        LocalDateTime now = attendanceService.now();
        LocalDate today = now.toLocalDate();
        if (date.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corrections can only be requested for today or past dates");
        }
        if (date.isBefore(today.minusDays(rules.correctionWindowDays()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Corrections can only be requested for the last " + rules.correctionWindowDays() + " days");
        }
        if (date.isBefore(employee.getJoiningDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That date is before your joining date");
        }
        if (request.requestedCheckIn() == null && request.requestedCheckOut() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a new check-in and/or check-out time");
        }
        for (LocalDateTime t : new LocalDateTime[] { request.requestedCheckIn(), request.requestedCheckOut() }) {
            if (t != null && !t.toLocalDate().equals(date)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Times must fall on " + date);
            }
            if (t != null && t.isAfter(now)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Times cannot be in the future");
            }
        }
        if (onApprovedLeave(employee.getId(), date)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are on approved leave on " + date + ", so there is nothing to correct");
        }
        LocalDateTime in = request.requestedCheckIn() != null ? request.requestedCheckIn()
                : record != null ? record.getCheckInTime() : null;
        LocalDateTime out = request.requestedCheckOut() != null ? request.requestedCheckOut()
                : record != null ? record.getCheckOutTime() : null;
        assertValidTimes(in, out);

        if (record != null && correctionRepository.existsByAttendanceRecordIdAndStatus(record.getId(), CorrectionStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "There is already a pending correction request for " + date);
        }
        if (record == null) {
            // The employee never checked in that day: a placeholder row gives the request something to point at.
            record = new AttendanceRecord();
            record.setEmployeeId(employee.getId());
            record.setAttendanceDate(date);
            record.setStatus(AttendanceStatus.ABSENT);
            try {
                record = recordRepository.saveAndFlush(record);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(HttpStatus.CONFLICT, "A record already exists for " + date + ". Please reload and try again.");
            }
        }

        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendanceRecord(record);
        correction.setRequestedBy(employee);
        correction.setRequestedCheckIn(request.requestedCheckIn());
        correction.setRequestedCheckOut(request.requestedCheckOut());
        correction.setReason(request.reason().trim());
        correction = correctionRepository.save(correction);
        auditService.log(actor.userId(), "ATTENDANCE_CORRECTION_REQUESTED", ENTITY, correction.getId(), ip);
        return toResponse(correction);
    }

    // -------------------------------------------------------------------- list

    @Transactional(readOnly = true)
    public PageResponse<CorrectionResponse> list(AuthenticatedUser actor, Scope requested, CorrectionStatus status,
            Pageable pageable) {
        Scope scope = requested != null ? requested : switch (actor.role()) {
            case EMPLOYEE -> Scope.MINE;
            case MANAGER -> Scope.TEAM;
            case HR_ADMIN, SYSTEM_ADMIN -> Scope.ALL;
        };
        if (scope == Scope.TEAM && actor.role() == Role.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only managers can view team corrections");
        }
        if (scope == Scope.ALL && !AttendanceService.isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only HR and administrators can view all corrections");
        }
        List<Specification<AttendanceCorrection>> specs = new ArrayList<>();
        switch (scope) {
            case MINE -> {
                if (actor.employeeId() == null) {
                    return PageResponse.of(new PageImpl<AttendanceCorrection>(List.of(), pageable, 0), c -> null);
                }
                specs.add(AttendanceCorrectionSpecifications.requestedBy(actor.employeeId()));
            }
            case TEAM -> {
                if (actor.employeeId() == null) {
                    return PageResponse.of(new PageImpl<AttendanceCorrection>(List.of(), pageable, 0), c -> null);
                }
                specs.add(AttendanceCorrectionSpecifications.ofTeamOf(actor.employeeId()));
            }
            case ALL -> { }
        }
        if (status != null) {
            specs.add(AttendanceCorrectionSpecifications.withStatus(status));
        }
        return PageResponse.of(correctionRepository.findAll(Specification.allOf(specs), pageable), this::toResponse);
    }

    // ------------------------------------------------------------------ decide

    @Transactional
    public CorrectionResponse approve(AuthenticatedUser actor, Long id, String comment, String ip) {
        AttendanceCorrection correction = lockPendingForDecision(actor, id);
        AttendanceRecord record = recordRepository.findByIdForUpdate(correction.getAttendanceRecord().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance record not found"));
        if (onApprovedLeave(record.getEmployeeId(), record.getAttendanceDate())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "The employee is on approved leave on " + record.getAttendanceDate() + ", so this cannot be applied");
        }

        LocalDateTime in = correction.getRequestedCheckIn() != null ? correction.getRequestedCheckIn() : record.getCheckInTime();
        LocalDateTime out = correction.getRequestedCheckOut() != null ? correction.getRequestedCheckOut() : record.getCheckOutTime();
        assertValidTimes(in, out);

        // Apply the times and recompute everything derived from them.
        record.setCheckInTime(in);
        record.setCheckOutTime(out);
        record.setWorkingHours(out == null ? null : rules.workingHours(in, out));
        record.setStatus(rules.statusFor(in, out));
        recordRepository.save(record);

        decide(correction, actor, CorrectionStatus.APPROVED, comment);
        auditService.log(actor.userId(), "ATTENDANCE_CORRECTION_APPROVED", ENTITY, id, ip);
        return toResponse(correction);
    }

    @Transactional
    public CorrectionResponse reject(AuthenticatedUser actor, Long id, String comment, String ip) {
        if (comment == null || comment.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please give a reason when rejecting a correction request");
        }
        AttendanceCorrection correction = lockPendingForDecision(actor, id);
        decide(correction, actor, CorrectionStatus.REJECTED, comment);
        auditService.log(actor.userId(), "ATTENDANCE_CORRECTION_REJECTED", ENTITY, id, ip);
        return toResponse(correction);
    }

    /** Loads under a row lock and enforces who may decide it, including the no-self-approval rule. */
    private AttendanceCorrection lockPendingForDecision(AuthenticatedUser actor, Long id) {
        AttendanceCorrection correction = correctionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Correction request not found"));
        Employee requester = correction.getRequestedBy();

        if (actor.employeeId() != null && actor.employeeId().equals(requester.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own correction request");
        }
        boolean directManager = requester.getManager() != null && requester.getManager().getId().equals(actor.employeeId());
        if (!AttendanceService.isHrOrAdmin(actor) && !directManager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only decide correction requests from your direct reports");
        }
        if (correction.getStatus() != CorrectionStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This request is already " + correction.getStatus());
        }
        return correction;
    }

    private void decide(AttendanceCorrection correction, AuthenticatedUser actor, CorrectionStatus status, String comment) {
        correction.setStatus(status);
        correction.setReviewedBy(actor.employeeId() == null ? null : employeeRepository.getReferenceById(actor.employeeId()));
        correction.setReviewedAt(Instant.now());
        correction.setReviewComment(comment == null || comment.isBlank() ? null : comment.trim());
    }

    // ------------------------------------------------------------------ helpers

    private boolean onApprovedLeave(Long employeeId, LocalDate date) {
        return !leaveRequestRepository.findOverlapping(List.of(employeeId), LeaveStatus.APPROVED, date, date).isEmpty();
    }

    private static void assertValidTimes(LocalDateTime in, LocalDateTime out) {
        if (in == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A check-in time is required");
        }
        if (out != null && !out.isAfter(in)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check-out must be after check-in");
        }
    }

    private CorrectionResponse toResponse(AttendanceCorrection c) {
        AttendanceRecord r = c.getAttendanceRecord();
        Employee e = c.getRequestedBy();
        Employee reviewer = c.getReviewedBy();
        return new CorrectionResponse(c.getId(), r.getId(), r.getAttendanceDate(), e.getId(), e.getEmployeeCode(),
                e.getFullName(), r.getCheckInTime(), r.getCheckOutTime(), c.getRequestedCheckIn(),
                c.getRequestedCheckOut(), c.getReason(), c.getStatus(), reviewer == null ? null : reviewer.getFullName(),
                c.getReviewComment(), c.getReviewedAt(), c.getCreatedAt());
    }
}
