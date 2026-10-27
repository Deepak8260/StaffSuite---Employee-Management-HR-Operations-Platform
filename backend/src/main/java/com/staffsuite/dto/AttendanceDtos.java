package com.staffsuite.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.CorrectionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public enum DayType { WORKING, WEEKEND, HOLIDAY, BEFORE_JOINING }

    /**
     * One calendar day for one employee. {@code status} is null when nothing applies (weekend, holiday,
     * today before check-in, before joining). ABSENT and ON_LEAVE are derived, not necessarily stored.
     */
    public record AttendanceDay(
            LocalDate date,
            DayType dayType,
            String holidayName,
            AttendanceStatus status,
            Long recordId,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            BigDecimal workingHours,
            String leaveTypeName,
            boolean pendingCorrection) {
    }

    /** Counts are employee-days within the range. */
    public record SummaryRow(Long id, String label, String employeeCode, int employeeCount, int present, int late,
            int halfDay, int absent, int onLeave, BigDecimal totalHours) {
    }

    public record AttendanceSummary(LocalDate from, LocalDate to, String groupBy, List<SummaryRow> rows,
            SummaryRow totals) {
    }

    /** Identify the day by {@code attendanceRecordId} or, when no record exists yet, by {@code attendanceDate}. */
    public record CorrectionRequest(
            Long attendanceRecordId,
            LocalDate attendanceDate,
            LocalDateTime requestedCheckIn,
            LocalDateTime requestedCheckOut,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record CorrectionResponse(
            Long id,
            Long attendanceRecordId,
            LocalDate attendanceDate,
            Long employeeId,
            String employeeCode,
            String employeeName,
            LocalDateTime currentCheckIn,
            LocalDateTime currentCheckOut,
            LocalDateTime requestedCheckIn,
            LocalDateTime requestedCheckOut,
            String reason,
            CorrectionStatus status,
            String reviewedByName,
            String reviewComment,
            Instant reviewedAt,
            Instant createdAt) {
    }
}
