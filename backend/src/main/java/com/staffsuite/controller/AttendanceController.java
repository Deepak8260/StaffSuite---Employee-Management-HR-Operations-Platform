package com.staffsuite.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.AttendanceDtos.AttendanceDay;
import com.staffsuite.dto.AttendanceDtos.AttendanceSummary;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.AttendanceService;
import com.staffsuite.service.AttendanceService.GroupBy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public AttendanceDay checkIn(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return attendanceService.checkIn(actor, http.getRemoteAddr());
    }

    @PostMapping("/check-out")
    public AttendanceDay checkOut(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return attendanceService.checkOut(actor, http.getRemoteAddr());
    }

    /** Aggregated counts: HR/Admin for everyone, a Manager for their own team only. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','MANAGER')")
    public AttendanceSummary summary(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "DEPARTMENT") GroupBy groupBy) {
        return attendanceService.summary(actor, from, to, departmentId, groupBy);
    }

    /** Day-by-day view for one employee: self, their direct manager, or HR/Admin. */
    @GetMapping("/{employeeId}")
    public List<AttendanceDay> days(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceService.getAttendance(actor, employeeId, from, to);
    }
}
