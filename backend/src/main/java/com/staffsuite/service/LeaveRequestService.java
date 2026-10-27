package com.staffsuite.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.LeaveDtos.AttachmentResponse;
import com.staffsuite.dto.LeaveDtos.CalendarLeave;
import com.staffsuite.dto.LeaveDtos.CalendarResponse;
import com.staffsuite.dto.LeaveDtos.HolidayResponse;
import com.staffsuite.dto.LeaveDtos.LeaveApplyRequest;
import com.staffsuite.dto.LeaveDtos.LeaveRequestResponse;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.LeaveBalance;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.PublicHoliday;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveRequestSpecifications;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.util.WorkingDays;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    /** Whose requests a listing covers. */
    public enum Scope { MINE, TEAM, ALL }

    public record AttachmentDownload(Resource resource, String contentType, String fileName) {
    }

    private static final String ENTITY = "LEAVE_REQUEST";
    private static final int MAX_CALENDAR_DAYS = 400;

    private final LeaveRequestRepository requestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PublicHolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService balanceService;
    private final AttachmentStorageService storageService;
    private final AuditService auditService;

    // -------------------------------------------------------------------- apply

    @Transactional
    public LeaveRequestResponse apply(AuthenticatedUser actor, LeaveApplyRequest request, String ip) {
        Long employeeId = requireEmployeeId(actor);
        Employee employee = employeeRepository.findById(employeeId).filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Your employee record is not active"));

        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date");
        }
        if (start.getYear() != end.getYear()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Leave cannot span two calendar years. Submit a separate request for each year.");
        }
        int year = start.getYear();
        int currentYear = LocalDate.now().getYear();
        if (year < currentYear - 1 || year > currentYear + 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Leave can only be requested for " + (currentYear - 1) + " to " + (currentYear + 1));
        }

        LeaveType type = leaveTypeRepository.findById(request.leaveTypeId()).filter(LeaveType::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Leave type not found"));

        // Total days exclude weekends and (non-optional) public holidays.
        int workingDays = WorkingDays.count(start, end, mandatoryHolidayDates(start, end));
        if (workingDays == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The selected dates fall only on weekends or public holidays, so no leave is needed");
        }
        BigDecimal total = BigDecimal.valueOf(workingDays);

        if (requestRepository.existsOverlapping(employeeId, start, end,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "These dates overlap with an existing pending or approved leave request");
        }

        String attachment = request.attachmentUrl() == null || request.attachmentUrl().isBlank()
                ? null : request.attachmentUrl().trim();
        if (attachment != null && !storageService.isOwnedAndPresent(attachment, employeeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The attachment is invalid. Please upload it again.");
        }

        // Lock the balance so two simultaneous applications cannot both spend the same days.
        LeaveBalance balance = balanceService.lockOrCreate(employeeId, type, year);
        BigDecimal pending = requestRepository.sumDays(employeeId, type.getId(), LeaveStatus.PENDING,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        BigDecimal available = balance.getRemainingDays().subtract(pending);
        if (total.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient " + type.getName() + " balance: you asked for "
                    + plain(total) + " day(s) but only " + plain(available.max(BigDecimal.ZERO))
                    + " are available (including days in your pending requests)");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setTotalDays(total);
        leave.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        leave.setAttachmentUrl(attachment);
        leave = requestRepository.save(leave);
        auditService.log(actor.userId(), "LEAVE_REQUESTED", ENTITY, leave.getId(), ip);
        return toResponse(leave);
    }

    public AttachmentResponse uploadAttachment(AuthenticatedUser actor, MultipartFile file) {
        return new AttachmentResponse(storageService.store(requireEmployeeId(actor), file));
    }

    // ------------------------------------------------------------------ decide

    @Transactional
    public LeaveRequestResponse approve(AuthenticatedUser actor, Long id, String comment, String ip) {
        LeaveRequest leave = lockPendingForDecision(actor, id);
        LeaveBalance balance = balanceService.lockOrCreate(leave.getEmployee().getId(), leave.getLeaveType(),
                leave.getStartDate().getYear());
        if (balance.getRemainingDays().compareTo(leave.getTotalDays()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "The employee no longer has enough "
                    + leave.getLeaveType().getName() + " balance to approve this request (remaining: "
                    + plain(balance.getRemainingDays()) + ", requested: " + plain(leave.getTotalDays()) + ")");
        }
        balance.setUsedDays(balance.getUsedDays().add(leave.getTotalDays()));
        balance.recalculate();
        record(leave, actor, LeaveStatus.APPROVED, comment);
        auditService.log(actor.userId(), "LEAVE_APPROVED", ENTITY, id, ip);
        return toResponse(leave);
    }

    @Transactional
    public LeaveRequestResponse reject(AuthenticatedUser actor, Long id, String comment, String ip) {
        if (comment == null || comment.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please give a reason when rejecting a leave request");
        }
        LeaveRequest leave = lockPendingForDecision(actor, id);
        record(leave, actor, LeaveStatus.REJECTED, comment);
        auditService.log(actor.userId(), "LEAVE_REJECTED", ENTITY, id, ip);
        return toResponse(leave);
    }

    /** Loads the request under a row lock and enforces who may decide it (including the no-self-approval rule). */
    private LeaveRequest lockPendingForDecision(AuthenticatedUser actor, Long id) {
        LeaveRequest leave = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        Employee requester = leave.getEmployee();

        // HARD RULE: nobody decides their own leave, whatever their role.
        if (actor.employeeId() != null && actor.employeeId().equals(requester.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own leave request");
        }
        boolean privileged = isHrOrAdmin(actor);
        boolean directManager = requester.getManager() != null
                && requester.getManager().getId().equals(actor.employeeId());
        if (!privileged && !directManager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only decide leave requests from your direct reports");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This request is already " + leave.getStatus());
        }
        return leave;
    }

    private void record(LeaveRequest leave, AuthenticatedUser actor, LeaveStatus status, String comment) {
        leave.setStatus(status);
        leave.setApprover(actor.employeeId() == null ? null : employeeRepository.getReferenceById(actor.employeeId()));
        leave.setApproverComment(comment == null || comment.isBlank() ? null : comment.trim());
        leave.setDecidedAt(Instant.now());
    }

    // ------------------------------------------------------------------ cancel

    @Transactional
    public LeaveRequestResponse cancel(AuthenticatedUser actor, Long id, String ip) {
        LeaveRequest leave = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (actor.employeeId() == null || !actor.employeeId().equals(leave.getEmployee().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only cancel your own leave requests");
        }
        switch (leave.getStatus()) {
            case PENDING -> leave.setStatus(LeaveStatus.CANCELLED);
            case APPROVED -> {
                if (!leave.getStartDate().isAfter(LocalDate.now())) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "This leave has already started, so it can no longer be cancelled");
                }
                LeaveBalance balance = balanceService.lockOrCreate(leave.getEmployee().getId(), leave.getLeaveType(),
                        leave.getStartDate().getYear());
                balance.setUsedDays(balance.getUsedDays().subtract(leave.getTotalDays()).max(BigDecimal.ZERO));
                balance.recalculate();
                leave.setStatus(LeaveStatus.CANCELLED);
            }
            default -> throw new ApiException(HttpStatus.CONFLICT,
                    "A " + leave.getStatus() + " request cannot be cancelled");
        }
        auditService.log(actor.userId(), "LEAVE_CANCELLED", ENTITY, id, ip);
        return toResponse(leave);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> list(AuthenticatedUser actor, Scope requested, LeaveStatus status,
            Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        Scope scope = resolveScope(actor, requested);
        List<Specification<LeaveRequest>> specs = new ArrayList<>();
        switch (scope) {
            case MINE -> {
                if (actor.employeeId() == null) {
                    return empty(pageable);
                }
                specs.add(LeaveRequestSpecifications.ofEmployee(actor.employeeId()));
            }
            case TEAM -> {
                if (actor.employeeId() == null) {
                    return empty(pageable);
                }
                specs.add(LeaveRequestSpecifications.ofTeamOf(actor.employeeId()));
            }
            case ALL -> {
                if (employeeId != null) {
                    specs.add(LeaveRequestSpecifications.ofEmployee(employeeId));
                }
            }
        }
        if (status != null) {
            specs.add(LeaveRequestSpecifications.withStatus(status));
        }
        if (from != null) {
            specs.add(LeaveRequestSpecifications.endingOnOrAfter(from));
        }
        if (to != null) {
            specs.add(LeaveRequestSpecifications.startingOnOrBefore(to));
        }
        Page<LeaveRequest> page = requestRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /** Approved leave plus public holidays in a window, limited to the caller's scope. */
    @Transactional(readOnly = true)
    public CalendarResponse calendar(AuthenticatedUser actor, Scope requested, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_CALENDAR_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Choose a date range of at most " + MAX_CALENDAR_DAYS + " days");
        }
        Scope scope = resolveScope(actor, requested);
        List<Specification<LeaveRequest>> specs = new ArrayList<>();
        specs.add(LeaveRequestSpecifications.withStatus(LeaveStatus.APPROVED));
        specs.add(LeaveRequestSpecifications.overlapping(from, to));
        switch (scope) {
            case MINE -> specs.add(LeaveRequestSpecifications.ofEmployee(actor.employeeId() == null ? -1L : actor.employeeId()));
            case TEAM -> {
                if (actor.employeeId() == null) {
                    specs.add(LeaveRequestSpecifications.ofEmployee(-1L));
                } else {
                    // a manager's calendar shows their reports and themselves
                    specs.add(Specification.anyOf(LeaveRequestSpecifications.ofTeamOf(actor.employeeId()),
                            LeaveRequestSpecifications.ofEmployee(actor.employeeId())));
                }
            }
            case ALL -> { }
        }
        List<CalendarLeave> leaves = requestRepository.findAll(Specification.allOf(specs)).stream()
                .map(r -> new CalendarLeave(r.getId(), r.getEmployee().getId(), r.getEmployee().getFullName(),
                        r.getLeaveType().getName(), r.getStartDate(), r.getEndDate(), r.getTotalDays()))
                .toList();
        List<HolidayResponse> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
                .stream().map(PublicHolidayService::toResponse).toList();
        return new CalendarResponse(holidays, leaves);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(AuthenticatedUser actor, Long id) {
        LeaveRequest leave = requestRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        Employee owner = leave.getEmployee();
        boolean self = owner.getId().equals(actor.employeeId());
        boolean directManager = owner.getManager() != null && owner.getManager().getId().equals(actor.employeeId());
        if (!self && !directManager && !isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to view this attachment");
        }
        if (leave.getAttachmentUrl() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This request has no attachment");
        }
        String url = leave.getAttachmentUrl();
        return new AttachmentDownload(storageService.load(url), storageService.contentTypeOf(url),
                "leave-request-" + id + "." + storageService.extensionOf(url));
    }

    // ------------------------------------------------------------------ helpers

    private Scope resolveScope(AuthenticatedUser actor, Scope requested) {
        Scope scope = requested != null ? requested : switch (actor.role()) {
            case EMPLOYEE -> Scope.MINE;
            case MANAGER -> Scope.TEAM;
            case HR_ADMIN, SYSTEM_ADMIN -> Scope.ALL;
        };
        if (scope == Scope.TEAM && actor.role() == Role.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only managers can view team leave");
        }
        if (scope == Scope.ALL && !isHrOrAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only HR and administrators can view all leave requests");
        }
        return scope;
    }

    private Set<LocalDate> mandatoryHolidayDates(LocalDate start, LocalDate end) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(start, end).stream()
                .filter(h -> !h.isOptional()).map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());
    }

    private static Long requireEmployeeId(AuthenticatedUser actor) {
        if (actor.employeeId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your login is not linked to an employee record");
        }
        return actor.employeeId();
    }

    private static boolean isHrOrAdmin(AuthenticatedUser actor) {
        return actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
    }

    private static PageResponse<LeaveRequestResponse> empty(Pageable pageable) {
        return PageResponse.of(new PageImpl<LeaveRequest>(List.of(), pageable, 0), r -> null);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private LeaveRequestResponse toResponse(LeaveRequest r) {
        Employee e = r.getEmployee();
        Employee a = r.getApprover();
        return new LeaveRequestResponse(r.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                r.getLeaveType().getId(), r.getLeaveType().getName(), r.getStartDate(), r.getEndDate(),
                r.getTotalDays(), r.getReason(), r.getStatus(), a == null ? null : a.getId(),
                a == null ? null : a.getFullName(), r.getApproverComment(), r.getAttachmentUrl() != null,
                r.getDecidedAt(), r.getCreatedAt());
    }
}
