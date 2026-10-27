package com.staffsuite.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.staffsuite.entity.LeaveStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LeaveDtos {

    private LeaveDtos() {
    }

    public record LeaveTypeRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0") @DecimalMax("366") BigDecimal annualEntitlementDays,
            Boolean carryForwardAllowed,
            @DecimalMin("0") @DecimalMax("366") BigDecimal maxCarryForwardDays,
            Boolean active) {
    }

    public record LeaveTypeResponse(Long id, String name, String description, BigDecimal annualEntitlementDays,
            boolean carryForwardAllowed, BigDecimal maxCarryForwardDays, boolean active) {
    }

    public record HolidayRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull LocalDate holidayDate,
            @Size(max = 500) String description,
            Boolean optional) {
    }

    public record HolidayResponse(Long id, String name, LocalDate holidayDate, String description, boolean optional) {
    }

    /** pendingDays are requested-but-undecided; availableDays = remainingDays - pendingDays. */
    public record LeaveBalanceResponse(Long leaveTypeId, String leaveTypeName, int year, BigDecimal allocatedDays,
            BigDecimal carriedForwardDays, BigDecimal usedDays, BigDecimal remainingDays, BigDecimal pendingDays,
            BigDecimal availableDays) {
    }

    public record InitializeResponse(int year, int created) {
    }

    public record LeaveApplyRequest(
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 1000) String reason,
            @Size(max = 500) String attachmentUrl) {
    }

    public record DecisionRequest(@Size(max = 1000) String comment) {
    }

    public record LeaveRequestResponse(
            Long id,
            Long employeeId,
            String employeeCode,
            String employeeName,
            Long leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalDays,
            String reason,
            LeaveStatus status,
            Long approverId,
            String approverName,
            String approverComment,
            boolean hasAttachment,
            Instant decidedAt,
            Instant createdAt) {
    }

    public record AttachmentResponse(String attachmentUrl) {
    }

    public record CalendarLeave(Long requestId, Long employeeId, String employeeName, String leaveTypeName,
            LocalDate startDate, LocalDate endDate, BigDecimal totalDays) {
    }

    public record CalendarResponse(List<HolidayResponse> holidays, List<CalendarLeave> leaves) {
    }
}
