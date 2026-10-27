package com.staffsuite.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.LeaveDtos.LeaveTypeRequest;
import com.staffsuite.dto.LeaveDtos.LeaveTypeResponse;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private static final String ENTITY = "LEAVE_TYPE";

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceService balanceService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list(boolean includeInactive) {
        List<LeaveType> types = includeInactive
                ? leaveTypeRepository.findAllByOrderByNameAsc()
                : leaveTypeRepository.findByActiveTrueOrderByNameAsc();
        return types.stream().map(LeaveTypeService::toResponse).toList();
    }

    @Transactional
    public LeaveTypeResponse create(LeaveTypeRequest request, AuthenticatedUser actor, String ip) {
        String name = request.name().trim();
        if (leaveTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A leave type named '" + name + "' already exists");
        }
        LeaveType type = new LeaveType();
        apply(type, request, name);
        type = leaveTypeRepository.save(type);
        // Give everyone a balance for the new type right away (current year).
        balanceService.initializeTypeForAllEmployees(type, LocalDate.now().getYear());
        auditService.log(actor.userId(), "LEAVE_TYPE_CREATED", ENTITY, type.getId(), ip);
        return toResponse(type);
    }

    /** Note: changing the entitlement does not alter balances that were already created for a year. */
    @Transactional
    public LeaveTypeResponse update(Long id, LeaveTypeRequest request, AuthenticatedUser actor, String ip) {
        LeaveType type = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave type not found"));
        String name = request.name().trim();
        if (leaveTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A leave type named '" + name + "' already exists");
        }
        apply(type, request, name);
        auditService.log(actor.userId(), "LEAVE_TYPE_UPDATED", ENTITY, id, ip);
        return toResponse(type);
    }

    private static void apply(LeaveType type, LeaveTypeRequest r, String name) {
        boolean carry = Boolean.TRUE.equals(r.carryForwardAllowed());
        type.setName(name);
        type.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        type.setAnnualEntitlementDays(r.annualEntitlementDays());
        type.setCarryForwardAllowed(carry);
        type.setMaxCarryForwardDays(carry && r.maxCarryForwardDays() != null ? r.maxCarryForwardDays() : BigDecimal.ZERO);
        if (r.active() != null) {
            type.setActive(r.active());
        }
    }

    private static LeaveTypeResponse toResponse(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getAnnualEntitlementDays(),
                t.isCarryForwardAllowed(), t.getMaxCarryForwardDays(), t.isActive());
    }
}
