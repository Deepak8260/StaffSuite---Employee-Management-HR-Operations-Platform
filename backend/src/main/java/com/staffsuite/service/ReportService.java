package com.staffsuite.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.AttendanceDtos.DayType;
import com.staffsuite.dto.ReportDtos.AttendancePoint;
import com.staffsuite.dto.ReportDtos.AttendanceTrendReport;
import com.staffsuite.dto.ReportDtos.CountItem;
import com.staffsuite.dto.ReportDtos.Granularity;
import com.staffsuite.dto.ReportDtos.HeadcountReport;
import com.staffsuite.dto.ReportDtos.HiresPoint;
import com.staffsuite.dto.ReportDtos.LeaveUsageItem;
import com.staffsuite.dto.ReportDtos.LeaveUtilizationReport;
import com.staffsuite.dto.ReportDtos.OnboardingReport;
import com.staffsuite.dto.ReportDtos.StatusCount;
import com.staffsuite.entity.AttendanceRecord;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.PublicHoliday;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.EmployeeSpecifications;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.util.WorkingDays;

import lombok.RequiredArgsConstructor;

/**
 * HR analytics. Everything here is read-only and computed from the existing tables: no report data is stored.
 * Callers must already be restricted to HR/Admin (the controller does that); this class does not look at roles.
 *
 * <p>Scope, so the numbers are never a surprise: headcount, attendance and leave cover <em>current</em>
 * employees (offboarded people are excluded); hiring counts everyone who was ever hired in the range.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_ATTENDANCE_DAYS = 366;
    private static final int MAX_LEAVE_DAYS = 1096;     // three years
    private static final int MAX_ONBOARDING_DAYS = 3660;  // ten years

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PublicHolidayRepository holidayRepository;
    private final Clock clock;

    // ---------------------------------------------------------------- defaults

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // --------------------------------------------------------------- headcount

    @Transactional(readOnly = true)
    public HeadcountReport headcount(Long departmentId) {
        requireDepartment(departmentId);

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : employeeRepository.headcountByDepartment(departmentId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        List<CountItem> byDepartment = new ArrayList<>();
        for (Department d : departmentRepository.findByActiveTrueOrderByNameAsc()) {
            if (departmentId == null || departmentId.equals(d.getId())) {
                byDepartment.add(new CountItem(d.getId(), d.getName(), counts.getOrDefault(d.getId(), 0L)));
                counts.remove(d.getId());
            }
        }
        // people still assigned to a department that has since been deactivated must not vanish from the total
        counts.forEach((id, n) -> byDepartment.add(new CountItem(id,
                departmentRepository.findById(id).map(Department::getName).orElse("Unknown department"), n)));
        long withoutDepartment = employeeRepository.headcountWithoutDepartment(departmentId);
        if (withoutDepartment > 0) {
            byDepartment.add(new CountItem(null, "No department", withoutDepartment));
        }

        Map<EmploymentStatus, Long> statuses = new EnumMap<>(EmploymentStatus.class);
        for (Object[] row : employeeRepository.headcountByStatus(departmentId)) {
            statuses.put((EmploymentStatus) row[0], (Long) row[1]);
        }
        List<StatusCount> byStatus = new ArrayList<>();
        for (EmploymentStatus s : EmploymentStatus.values()) {
            // TERMINATED people are offboarded and so never part of current headcount; show it only if it somehow is
            if (s != EmploymentStatus.TERMINATED || statuses.getOrDefault(s, 0L) > 0) {
                byStatus.add(new StatusCount(s, statuses.getOrDefault(s, 0L)));
            }
        }
        long total = byStatus.stream().mapToLong(StatusCount::count).sum();
        return new HeadcountReport(today(), departmentId, total, byDepartment, byStatus);
    }

    // -------------------------------------------------------- attendance trend

    @Transactional(readOnly = true)
    public AttendanceTrendReport attendanceTrend(LocalDate from, LocalDate to, Long departmentId,
            Granularity granularity) {
        LocalDate start = from != null ? from : today().withDayOfMonth(1);
        LocalDate end = to != null ? to : today();
        Granularity grain = granularity != null ? granularity : Granularity.DAILY;
        validateRange(start, end, MAX_ATTENDANCE_DAYS);
        requireDepartment(departmentId);

        LocalDate today = today();
        LocalDate through = end.isAfter(today) ? today : end;   // nothing has happened after today
        List<Specification<Employee>> specs = new ArrayList<>();
        specs.add(EmployeeSpecifications.notDeleted());
        if (departmentId != null) {
            specs.add(EmployeeSpecifications.inDepartment(departmentId));
        }
        List<Employee> employees = employeeRepository.findAll(Specification.allOf(specs), Pageable.unpaged()).getContent();

        // one bucket per period, in order, even when nothing happened in it, so the chart has no gaps
        Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
        for (LocalDate d = periodStart(start, grain); !d.isAfter(through); d = nextPeriod(d, grain)) {
            if (!start.isAfter(periodEnd(d, grain))) {
                buckets.put(d, new int[5]);
            }
        }

        if (!employees.isEmpty() && !start.isAfter(through)) {
            List<Long> ids = employees.stream().map(Employee::getId).toList();
            Map<Long, Map<LocalDate, AttendanceRecord>> records = new HashMap<>();
            attendanceRecordRepository.findByEmployeeIdInAndAttendanceDateBetween(ids, start, through)
                    .forEach(r -> records.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).put(r.getAttendanceDate(), r));
            Map<Long, List<LeaveRequest>> leaves = leaveRequestRepository
                    .findOverlapping(ids, LeaveStatus.APPROVED, start, through).stream()
                    .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));
            Map<LocalDate, PublicHoliday> holidays = holidayRepository
                    .findByHolidayDateBetweenOrderByHolidayDateAsc(start, through).stream()
                    .collect(Collectors.toMap(PublicHoliday::getHolidayDate, h -> h));

            for (Employee e : employees) {
                LocalDate first = start.isBefore(e.getJoiningDate()) ? e.getJoiningDate() : start;
                for (LocalDate d = first; !d.isAfter(through); d = d.plusDays(1)) {
                    DayType type = AttendanceService.dayType(e, d, holidays);
                    boolean onLeave = AttendanceService.findLeave(leaves.getOrDefault(e.getId(), List.of()), d) != null;
                    AttendanceRecord record = records.getOrDefault(e.getId(), Map.of()).get(d);
                    AttendanceStatus status = AttendanceService.effectiveStatus(record, onLeave, type, d, today);
                    if (status != null) {
                        buckets.get(periodStart(d, grain))[slot(status)]++;
                    }
                }
            }
        }

        List<AttendancePoint> points = new ArrayList<>();
        int[] total = new int[5];
        buckets.forEach((periodStart, c) -> {
            LocalDate s = periodStart.isBefore(start) ? start : periodStart;
            LocalDate e = periodEnd(periodStart, grain);
            points.add(new AttendancePoint(s, e.isAfter(through) ? through : e, c[0], c[1], c[2], c[3], c[4]));
            for (int i = 0; i < total.length; i++) {
                total[i] += c[i];
            }
        });
        AttendancePoint totals = new AttendancePoint(start, through.isBefore(start) ? start : through,
                total[0], total[1], total[2], total[3], total[4]);
        return new AttendanceTrendReport(start, end, departmentId, grain, employees.size(), points, totals);
    }

    /** present, late, half day, absent, on leave. */
    private static int slot(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> 0;
            case LATE -> 1;
            case HALF_DAY -> 2;
            case ABSENT -> 3;
            case ON_LEAVE -> 4;
        };
    }

    private static LocalDate periodStart(LocalDate d, Granularity g) {
        return switch (g) {
            case DAILY -> d;
            case WEEKLY -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> d.withDayOfMonth(1);
        };
    }

    private static LocalDate periodEnd(LocalDate periodStart, Granularity g) {
        return switch (g) {
            case DAILY -> periodStart;
            case WEEKLY -> periodStart.plusDays(6);
            case MONTHLY -> periodStart.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private static LocalDate nextPeriod(LocalDate periodStart, Granularity g) {
        return periodEnd(periodStart, g).plusDays(1);
    }

    // ------------------------------------------------------- leave utilisation

    @Transactional(readOnly = true)
    public LeaveUtilizationReport leaveUtilization(LocalDate from, LocalDate to, Long departmentId) {
        LocalDate start = from != null ? from : LocalDate.of(today().getYear(), 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(today().getYear(), 12, 31);
        validateRange(start, end, MAX_LEAVE_DAYS);
        requireDepartment(departmentId);

        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(start, end).stream()
                .filter(h -> !h.isOptional()).map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());

        Map<Long, long[]> byType = new LinkedHashMap<>();         // id -> {days, requests}
        Map<Long, String> typeNames = new HashMap<>();
        Map<Long, long[]> byDepartment = new LinkedHashMap<>();
        Map<Long, String> departmentNames = new HashMap<>();
        for (LeaveType t : leaveTypeRepository.findByActiveTrueOrderByNameAsc()) {
            byType.put(t.getId(), new long[2]);
            typeNames.put(t.getId(), t.getName());
        }
        for (Department d : departmentRepository.findByActiveTrueOrderByNameAsc()) {
            if (departmentId == null || departmentId.equals(d.getId())) {
                byDepartment.put(d.getId(), new long[2]);
                departmentNames.put(d.getId(), d.getName());
            }
        }

        long totalDays = 0;
        long totalRequests = 0;
        for (LeaveRequest r : leaveRequestRepository.findForUtilization(LeaveStatus.APPROVED, start, end, departmentId)) {
            // only the part of the request that falls inside the range counts, and only working days
            LocalDate s = r.getStartDate().isBefore(start) ? start : r.getStartDate();
            LocalDate e = r.getEndDate().isAfter(end) ? end : r.getEndDate();
            long days = WorkingDays.count(s, e, holidays);

            LeaveType type = r.getLeaveType();
            typeNames.putIfAbsent(type.getId(), type.getName());
            add(byType, type.getId(), days);

            Department dept = r.getEmployee().getDepartment();
            Long deptKey = dept == null ? -1L : dept.getId();
            departmentNames.putIfAbsent(deptKey, dept == null ? "No department" : dept.getName());
            add(byDepartment, deptKey, days);

            totalDays += days;
            totalRequests++;
        }
        return new LeaveUtilizationReport(start, end, departmentId, totalDays, totalRequests,
                usageItems(byType, typeNames), usageItems(byDepartment, departmentNames));
    }

    private static void add(Map<Long, long[]> map, Long key, long days) {
        long[] v = map.computeIfAbsent(key, k -> new long[2]);
        v[0] += days;
        v[1]++;
    }

    private static List<LeaveUsageItem> usageItems(Map<Long, long[]> values, Map<Long, String> names) {
        return values.entrySet().stream()
                .map(en -> new LeaveUsageItem(en.getKey() < 0 ? null : en.getKey(), names.get(en.getKey()),
                        en.getValue()[0], en.getValue()[1]))
                .sorted(Comparator.comparing(LeaveUsageItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // -------------------------------------------------------------- onboarding

    @Transactional(readOnly = true)
    public OnboardingReport onboarding(LocalDate from, LocalDate to, Long departmentId) {
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : YearMonth.from(end).minusMonths(11).atDay(1);
        validateRange(start, end, MAX_ONBOARDING_DAYS);
        requireDepartment(departmentId);

        Map<YearMonth, long[]> months = new LinkedHashMap<>();   // {hires, stillEmployed}
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            months.put(m, new long[2]);
        }
        for (Object[] row : employeeRepository.hiresBetween(start, end, departmentId)) {
            long[] bucket = months.get(YearMonth.from((LocalDate) row[0]));
            bucket[0]++;
            if (!(Boolean) row[1]) {
                bucket[1]++;
            }
        }
        List<HiresPoint> points = new ArrayList<>();
        long hires = 0;
        long still = 0;
        for (Map.Entry<YearMonth, long[]> en : months.entrySet()) {
            points.add(new HiresPoint(en.getKey().atDay(1), en.getValue()[0], en.getValue()[1]));
            hires += en.getValue()[0];
            still += en.getValue()[1];
        }
        return new OnboardingReport(start, end, departmentId, hires, still, points);
    }

    // ----------------------------------------------------------------- helpers

    private void requireDepartment(Long departmentId) {
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Department not found");
        }
    }

    private static void validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date cannot be before the start date");
        }
        if (from.plusDays(maxDays).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a date range of at most " + maxDays + " days");
        }
    }
}
