package com.staffsuite.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.LeaveDtos.InitializeResponse;
import com.staffsuite.dto.LeaveDtos.LeaveBalanceResponse;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.LeaveBalance;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveBalanceRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    // ----------------------------------------------------------- initialisation

    /** Creates any missing balance rows (one per active leave type) for an employee. Idempotent. */
    @Transactional
    public int initializeForEmployee(Long employeeId, int year) {
        int created = 0;
        for (LeaveType type : leaveTypeRepository.findByActiveTrueOrderByNameAsc()) {
            if (balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year).isEmpty()) {
                createBalance(employeeId, type, year);
                created++;
            }
        }
        return created;
    }

    /** Yearly roll-over entry point: initialise every active employee for the given year. Idempotent. */
    @Transactional
    public InitializeResponse initializeAll(int year, AuthenticatedUser actor, String ip) {
        int created = 0;
        for (Employee employee : employeeRepository.findAll()) {
            if (!employee.isDeleted()) {
                created += initializeForEmployee(employee.getId(), year);
            }
        }
        auditService.log(actor.userId(), "LEAVE_BALANCES_INITIALIZED", "LEAVE_BALANCE", null, ip);
        return new InitializeResponse(year, created);
    }

    /** Called when a new leave type is created so existing employees get a balance for it straight away. */
    @Transactional
    public void initializeTypeForAllEmployees(LeaveType type, int year) {
        for (Employee employee : employeeRepository.findAll()) {
            if (!employee.isDeleted()
                    && balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), type.getId(), year)
                            .isEmpty()) {
                createBalance(employee.getId(), type, year);
            }
        }
    }

    /**
     * New balance = annual entitlement + carry-forward, where carry-forward is
     * min(previous year's remaining days, max_carry_forward_days) if the type allows it.
     */
    private LeaveBalance createBalance(Long employeeId, LeaveType type, int year) {
        BigDecimal carried = BigDecimal.ZERO;
        if (type.isCarryForwardAllowed()) {
            carried = balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year - 1)
                    .map(prev -> prev.getRemainingDays().max(BigDecimal.ZERO).min(type.getMaxCarryForwardDays()))
                    .orElse(BigDecimal.ZERO);
        }
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployeeId(employeeId);
        balance.setLeaveTypeId(type.getId());
        balance.setYear(year);
        balance.setCarriedForwardDays(carried);
        balance.setAllocatedDays(type.getAnnualEntitlementDays().add(carried));
        return balanceRepository.save(balance);
    }

    /** Returns the row-locked balance for a type/year, creating it first if it does not exist yet. */
    @Transactional
    public LeaveBalance lockOrCreate(Long employeeId, LeaveType type, int year) {
        return balanceRepository.findForUpdate(employeeId, type.getId(), year)
                .orElseGet(() -> createBalance(employeeId, type, year));
    }

    // ------------------------------------------------------------------ queries

    @Transactional
    public List<LeaveBalanceResponse> getBalances(AuthenticatedUser actor, Long employeeId, Integer requestedYear) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
        boolean privileged = actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
        boolean self = employeeId.equals(actor.employeeId());
        boolean directManager = employee.getManager() != null && employee.getManager().getId().equals(actor.employeeId());
        if (!privileged && !self && !directManager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to view this employee's leave balances");
        }

        int year = requestedYear != null ? requestedYear : LocalDate.now().getYear();
        if (!employee.isDeleted()) {
            initializeForEmployee(employeeId, year);
        }

        Map<Long, LeaveType> types = new HashMap<>();
        leaveTypeRepository.findAll().forEach(t -> types.put(t.getId(), t));
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        return balanceRepository.findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(employeeId, year).stream()
                .map(b -> {
                    BigDecimal pending = leaveRequestRepository.sumDays(employeeId, b.getLeaveTypeId(),
                            LeaveStatus.PENDING, yearStart, yearEnd);
                    return new LeaveBalanceResponse(b.getLeaveTypeId(), types.get(b.getLeaveTypeId()).getName(),
                            b.getYear(), b.getAllocatedDays(), b.getCarriedForwardDays(), b.getUsedDays(),
                            b.getRemainingDays(), pending, b.getRemainingDays().subtract(pending));
                })
                .toList();
    }
}
