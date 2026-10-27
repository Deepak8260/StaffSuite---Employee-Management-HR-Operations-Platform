package com.staffsuite.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.AttendanceDtos.AttendanceDay;
import com.staffsuite.dto.AttendanceDtos.AttendanceSummary;
import com.staffsuite.dto.AttendanceDtos.DayType;
import com.staffsuite.dto.AttendanceDtos.SummaryRow;
import com.staffsuite.entity.AttendanceRecord;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.CorrectionStatus;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.PublicHoliday;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.EmployeeSpecifications;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.util.WorkingDays;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    public enum GroupBy { DEPARTMENT, EMPLOYEE }

    private static final String ENTITY = "ATTENDANCE";
    private static final int MAX_RANGE_DAYS = 93;

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PublicHolidayRepository holidayRepository;
    private final AttendanceRules rules;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------ check in / out

    @Transactional
    public AttendanceDay checkIn(AuthenticatedUser actor, String ip) {
        Employee employee = activeEmployee(actor);
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        if (!leaveRequestRepository.findOverlapping(List.of(employee.getId()), LeaveStatus.APPROVED, today, today)
                .isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You are on approved leave today, so you cannot check in");
        }
        AttendanceRecord record = recordRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (record != null && record.getCheckInTime() != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You have already checked in today at " + record.getCheckInTime().toLocalTime());
        }
        if (record == null) {
            record = new AttendanceRecord();
            record.setEmployeeId(employee.getId());
            record.setAttendanceDate(today);
        }
        record.setCheckInTime(now);
        record.setStatus(rules.statusFor(now, null));
        try {
            recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // two check-ins raced; the unique (employee, date) constraint let only one through
            throw new ApiException(HttpStatus.CONFLICT, "You have already checked in today");
        }
        auditService.log(actor.userId(), "ATTENDANCE_CHECK_IN", ENTITY, record.getId(), ip);
        return dayFor(employee, today);
    }

    @Transactional
    public AttendanceDay checkOut(AuthenticatedUser actor, String ip) {
        Employee employee = activeEmployee(actor);
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        AttendanceRecord record = recordRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (record == null || record.getCheckInTime() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "You have not checked in today, so there is nothing to check out of");
        }
        if (record.getCheckOutTime() != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You have already checked out today at " + record.getCheckOutTime().toLocalTime());
        }
        if (now.isBefore(record.getCheckInTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check-out time cannot be before your check-in time");
        }
        record.setCheckOutTime(now);
        record.setWorkingHours(rules.workingHours(record.getCheckInTime(), now));
        record.setStatus(rules.statusFor(record.getCheckInTime(), now));
        recordRepository.save(record);
        auditService.log(actor.userId(), "ATTENDANCE_CHECK_OUT", ENTITY, record.getId(), ip);
        return dayFor(employee, today);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<AttendanceDay> getAttendance(AuthenticatedUser actor, Long employeeId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
        boolean self = employeeId.equals(actor.employeeId());
        boolean directManager = employee.getManager() != null && employee.getManager().getId().equals(actor.employeeId());
        if (!self && !directManager && !isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to view this employee's attendance");
        }
        return buildDays(employee, from, to);
    }

    /**
     * Aggregates employee-days in [from, to]. HR/Admin cover everyone (optionally one department); a
     * Manager only ever covers their own direct reports.
     */
    @Transactional(readOnly = true)
    public AttendanceSummary summary(AuthenticatedUser actor, LocalDate from, LocalDate to, Long departmentId,
            GroupBy groupBy) {
        validateRange(from, to);
        List<org.springframework.data.jpa.domain.Specification<Employee>> specs = new ArrayList<>();
        specs.add(EmployeeSpecifications.notDeleted());
        if (!isHrOrAdmin(actor)) {
            if (actor.role() != Role.MANAGER) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
            }
            if (actor.employeeId() == null) {
                return emptySummary(from, to, groupBy);
            }
            specs.add(EmployeeSpecifications.managedBy(actor.employeeId()));
        }
        if (departmentId != null) {
            specs.add(EmployeeSpecifications.inDepartment(departmentId));
        }
        List<Employee> employees = employeeRepository
                .findAll(org.springframework.data.jpa.domain.Specification.allOf(specs), Pageable.unpaged()).getContent();
        if (employees.isEmpty()) {
            return emptySummary(from, to, groupBy);
        }

        LocalDate today = LocalDate.now(clock);
        List<Long> ids = employees.stream().map(Employee::getId).toList();
        Map<Long, Map<LocalDate, AttendanceRecord>> records = new HashMap<>();
        recordRepository.findByEmployeeIdInAndAttendanceDateBetween(ids, from, to)
                .forEach(r -> records.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).put(r.getAttendanceDate(), r));
        Map<Long, List<LeaveRequest>> leaves = leaveRequestRepository
                .findOverlapping(ids, LeaveStatus.APPROVED, from, to).stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));
        Map<LocalDate, PublicHoliday> holidays = holidaysBetween(from, to);

        Map<Long, Tally> tallies = new LinkedHashMap<>();   // key = employee id or department id (-1 = none)
        Map<Long, String> labels = new HashMap<>();
        Map<Long, String> codes = new HashMap<>();
        for (Employee e : employees) {
            long key = groupBy == GroupBy.EMPLOYEE ? e.getId() : e.getDepartment() == null ? -1L : e.getDepartment().getId();
            labels.putIfAbsent(key, groupBy == GroupBy.EMPLOYEE ? e.getFullName()
                    : e.getDepartment() == null ? "No department" : e.getDepartment().getName());
            if (groupBy == GroupBy.EMPLOYEE) {
                codes.put(key, e.getEmployeeCode());
            }
            Tally tally = tallies.computeIfAbsent(key, k -> new Tally());
            tally.employees++;

            LocalDate start = from.isBefore(e.getJoiningDate()) ? e.getJoiningDate() : from;
            LocalDate end = to.isAfter(today) ? today : to;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                DayType type = dayType(e, d, holidays);
                boolean onLeave = findLeave(leaves.getOrDefault(e.getId(), List.of()), d) != null;
                AttendanceRecord record = records.getOrDefault(e.getId(), Map.of()).get(d);
                AttendanceStatus status = effectiveStatus(record, onLeave, type, d, today);
                if (status == null) {
                    continue;
                }
                tally.add(status);
                if (record != null && record.getWorkingHours() != null) {
                    tally.hours = tally.hours.add(record.getWorkingHours());
                }
            }
        }

        List<SummaryRow> rows = tallies.entrySet().stream()
                .map(en -> en.getValue().toRow(en.getKey() < 0 ? null : en.getKey(), labels.get(en.getKey()), codes.get(en.getKey())))
                .sorted(Comparator.comparing(SummaryRow::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Tally total = new Tally();
        tallies.values().forEach(total::addAll);
        return new AttendanceSummary(from, to, groupBy.name(), rows, total.toRow(null, "Total", null));
    }

    // ------------------------------------------------------ shared day building

    /** Used by corrections and check-in/out: the day's view for one employee. */
    AttendanceDay dayFor(Employee employee, LocalDate date) {
        return buildDays(employee, date, date).get(0);
    }

    private List<AttendanceDay> buildDays(Employee employee, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, AttendanceRecord> records = recordRepository
                .findByEmployeeIdAndAttendanceDateBetween(employee.getId(), from, to).stream()
                .collect(Collectors.toMap(AttendanceRecord::getAttendanceDate, r -> r));
        List<LeaveRequest> leaves = leaveRequestRepository
                .findOverlapping(List.of(employee.getId()), LeaveStatus.APPROVED, from, to);
        Map<LocalDate, PublicHoliday> holidays = holidaysBetween(from, to);
        Set<Long> pending = records.isEmpty() ? Set.of() : new HashSet<>(correctionRepository.recordIdsWithStatus(
                records.values().stream().map(AttendanceRecord::getId).toList(), CorrectionStatus.PENDING));

        List<AttendanceDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayType type = dayType(employee, d, holidays);
            LeaveRequest leave = findLeave(leaves, d);
            AttendanceRecord record = records.get(d);
            AttendanceStatus status = effectiveStatus(record, leave != null, type, d, today);
            PublicHoliday holiday = holidays.get(d);
            days.add(new AttendanceDay(d, type, holiday == null ? null : holiday.getName(), status,
                    record == null ? null : record.getId(),
                    record == null ? null : record.getCheckInTime(),
                    record == null ? null : record.getCheckOutTime(),
                    record == null ? null : record.getWorkingHours(),
                    status == AttendanceStatus.ON_LEAVE && leave != null ? leave.getLeaveType().getName() : null,
                    record != null && pending.contains(record.getId())));
        }
        return days;
    }

    private static DayType dayType(Employee employee, LocalDate d, Map<LocalDate, PublicHoliday> holidays) {
        if (d.isBefore(employee.getJoiningDate())) {
            return DayType.BEFORE_JOINING;
        }
        if (WorkingDays.isWeekend(d)) {
            return DayType.WEEKEND;
        }
        PublicHoliday holiday = holidays.get(d);
        return holiday != null && !holiday.isOptional() ? DayType.HOLIDAY : DayType.WORKING;
    }

    /**
     * What a day counts as. A real check-in always wins. Otherwise approved leave shows as ON_LEAVE (never
     * ABSENT); otherwise a past working day with nothing recorded is ABSENT. Today before check-in and
     * non-working days have no status.
     */
    private static AttendanceStatus effectiveStatus(AttendanceRecord record, boolean onLeave, DayType type,
            LocalDate date, LocalDate today) {
        if (record != null && record.getCheckInTime() != null) {
            return record.getStatus();
        }
        if (type != DayType.WORKING) {
            return null;
        }
        if (onLeave) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (record != null) {
            return record.getStatus();   // placeholder created for a correction request
        }
        return date.isBefore(today) ? AttendanceStatus.ABSENT : null;
    }

    private static LeaveRequest findLeave(List<LeaveRequest> leaves, LocalDate d) {
        for (LeaveRequest l : leaves) {
            if (!d.isBefore(l.getStartDate()) && !d.isAfter(l.getEndDate())) {
                return l;
            }
        }
        return null;
    }

    private Map<LocalDate, PublicHoliday> holidaysBetween(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to).stream()
                .collect(Collectors.toMap(PublicHoliday::getHolidayDate, h -> h));
    }

    // ------------------------------------------------------------------ helpers

    LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    Employee activeEmployee(AuthenticatedUser actor) {
        if (actor.employeeId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your login is not linked to an employee record");
        }
        return employeeRepository.findById(actor.employeeId()).filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Your employee record is not active"));
    }

    static boolean isHrOrAdmin(AuthenticatedUser actor) {
        return actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date cannot be before the start date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a date range of at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private static AttendanceSummary emptySummary(LocalDate from, LocalDate to, GroupBy groupBy) {
        return new AttendanceSummary(from, to, groupBy.name(), List.of(),
                new Tally().toRow(null, "Total", null));
    }

    /** Mutable counters for one summary row. */
    private static final class Tally {
        int employees;
        int present;
        int late;
        int halfDay;
        int absent;
        int onLeave;
        BigDecimal hours = BigDecimal.ZERO;

        void add(AttendanceStatus s) {
            switch (s) {
                case PRESENT -> present++;
                case LATE -> late++;
                case HALF_DAY -> halfDay++;
                case ABSENT -> absent++;
                case ON_LEAVE -> onLeave++;
            }
        }

        void addAll(Tally o) {
            employees += o.employees;
            present += o.present;
            late += o.late;
            halfDay += o.halfDay;
            absent += o.absent;
            onLeave += o.onLeave;
            hours = hours.add(o.hours);
        }

        SummaryRow toRow(Long id, String label, String code) {
            return new SummaryRow(id, label, code, employees, present, late, halfDay, absent, onLeave, hours);
        }
    }
}
