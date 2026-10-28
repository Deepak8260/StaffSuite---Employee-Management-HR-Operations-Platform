package com.staffsuite.dto;

import java.time.LocalDate;
import java.util.List;

import com.staffsuite.entity.EmploymentStatus;

public final class ReportDtos {

    private ReportDtos() {
    }

    public enum Granularity { DAILY, WEEKLY, MONTHLY }

    /** A named count; {@code id} is null for the "No department" row. */
    public record CountItem(Long id, String name, long count) {
    }

    public record StatusCount(EmploymentStatus status, long count) {
    }

    /** A snapshot of today's workforce: current employees only (offboarded people are not headcount). */
    public record HeadcountReport(LocalDate asOf, Long departmentId, long total, List<CountItem> byDepartment,
            List<StatusCount> byStatus) {
    }

    /** Employee-days in one period, counted exactly as the Attendance module counts them. */
    public record AttendancePoint(LocalDate periodStart, LocalDate periodEnd, int present, int late, int halfDay,
            int absent, int onLeave) {
    }

    /** {@code periodStart}/{@code periodEnd} are clipped to the requested range and to today. */
    public record AttendanceTrendReport(LocalDate from, LocalDate to, Long departmentId, Granularity granularity,
            int employees, List<AttendancePoint> points, AttendancePoint totals) {
    }

    public record LeaveUsageItem(Long id, String name, long days, long requests) {
    }

    /** Working days of approved leave that fall inside the range. */
    public record LeaveUtilizationReport(LocalDate from, LocalDate to, Long departmentId, long totalDays,
            long totalRequests, List<LeaveUsageItem> byType, List<LeaveUsageItem> byDepartment) {
    }

    public record HiresPoint(LocalDate monthStart, long hires, long stillEmployed) {
    }

    public record OnboardingReport(LocalDate from, LocalDate to, Long departmentId, long totalHires,
            long totalStillEmployed, List<HiresPoint> months) {
    }
}
