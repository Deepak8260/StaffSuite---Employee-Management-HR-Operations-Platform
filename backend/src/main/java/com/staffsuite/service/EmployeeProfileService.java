package com.staffsuite.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staffsuite.dto.AttendanceDtos.AttendanceDay;
import com.staffsuite.dto.EmployeeDtos.EmployeeProfile;
import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.dto.EmployeeDtos.ProfileAttendanceSummary;
import com.staffsuite.dto.EmployeeDtos.ProfileLeaveSummary;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.EmployeeService.ProfileEmployee;

import lombok.RequiredArgsConstructor;

/**
 * Builds the one-call profile by composing the existing services. Each of them applies its own visibility rules,
 * so nothing here decides who may see what beyond "full or directory-level", which EmployeeService reports.
 * Deliberately not transactional itself: the balances call may create missing rows and must own its transaction.
 */
@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    private final EmployeeService employeeService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final AttendanceService attendanceService;
    private final DocumentService documentService;
    private final ProfileCompletionService completionService;
    private final Clock clock;

    public EmployeeProfile profile(AuthenticatedUser actor, Long id) {
        ProfileEmployee basics = employeeService.getForProfile(actor, id);
        EmployeeResponse e = basics.employee();
        ProfileLeaveSummary leave = new ProfileLeaveSummary(
                leaveBalanceService.getBalances(actor, id, null), leaveRequestService.countPending(actor, id));
        return new EmployeeProfile(e.id(), "FULL", e.fullName(), e.jobTitle(), e.departmentName(), e.profilePhotoUrl(),
                e.employeeCode(), e.email(), e.phone(), e.address(), e.emergencyContact(), e.dateOfBirth(), e.joiningDate(), e.employmentStatus(),
                e.designationName(), e.managerId(), e.managerName(), leave, attendanceThisMonth(actor, id),
                documentService.list(actor, id), employeeService.directReports(id),
                completionService.of(id, e.profilePhotoUrl() != null, e.phone(), e.address(), e.emergencyContact()));
    }

    /** Counts the status each day of this month (up to today) already has; the day rules live in AttendanceService. */
    private ProfileAttendanceSummary attendanceThisMonth(AuthenticatedUser actor, Long id) {
        LocalDate today = LocalDate.now(clock);
        List<AttendanceDay> days = attendanceService.getAttendance(actor, id, today.withDayOfMonth(1), today);
        int present = 0;
        int absent = 0;
        int late = 0;
        AttendanceStatus todayStatus = null;
        for (AttendanceDay d : days) {
            if (d.status() == AttendanceStatus.PRESENT) {
                present++;
            }
            if (d.status() == AttendanceStatus.ABSENT) {
                absent++;
            }
            if (d.status() == AttendanceStatus.LATE) {
                late++;
            }
            if (d.date().equals(today)) {
                todayStatus = d.status();
            }
        }
        return new ProfileAttendanceSummary(YearMonth.from(today).toString(), present, absent, late, todayStatus);
    }
}
