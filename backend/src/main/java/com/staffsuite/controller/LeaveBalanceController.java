package com.staffsuite.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.LeaveDtos.InitializeResponse;
import com.staffsuite.dto.LeaveDtos.LeaveBalanceResponse;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.LeaveBalanceService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService balanceService;

    /** Self, the employee's direct manager, or HR/Admin. Missing balance rows are created on first view. */
    @GetMapping("/{employeeId}")
    public List<LeaveBalanceResponse> balances(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        return balanceService.getBalances(actor, employeeId, year);
    }

    /** Yearly roll-over: creates the year's balances (with carry-forward) for every active employee. */
    @PostMapping("/initialize")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public InitializeResponse initialize(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer year, HttpServletRequest http) {
        return balanceService.initializeAll(year != null ? year : LocalDate.now().getYear(), actor,
                http.getRemoteAddr());
    }
}
