package com.staffsuite.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.EmployeeDtos.DirectoryEntry;
import com.staffsuite.dto.EmployeeDtos.EmployeeRequest;
import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.dto.EmployeeDtos.SelfProfileRequest;
import com.staffsuite.dto.EmployeeDtos.UserAccountRequest;
import com.staffsuite.dto.OrganizationDtos.ReassignRequest;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Designation;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.exception.ApiException;
import com.staffsuite.mapper.EmployeeMapper;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.EmployeeSpecifications;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.util.EmployeeCodeGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String ENTITY = "EMPLOYEE";
    private static final int MAX_MANAGER_CHAIN = 100;

    /** Roles an HR_ADMIN may hand out when onboarding; only SYSTEM_ADMIN may grant the rest. */
    private static final Set<Role> HR_GRANTABLE_ROLES = Set.of(Role.EMPLOYEE, Role.MANAGER);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmployeeMapper mapper;
    private final LeaveBalanceService leaveBalanceService;
    private final ProfileCompletionService profileCompletion;

    /** The employee as the profile page may show them: {@code full} is false for directory-level viewers. */
    public record ProfileEmployee(EmployeeResponse employee, boolean full) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(AuthenticatedUser actor, Long departmentId, EmploymentStatus status,
            Long managerId, String search, boolean includeOffboarded, Pageable pageable) {
        if (!isHrOrAdmin(actor)) {
            if (actor.role() != Role.MANAGER) {
                throw new AccessDeniedException("Access denied");
            }
            if (actor.employeeId() == null) {
                return PageResponse.of(new PageImpl<Employee>(List.of(), pageable, 0), mapper::toResponse);
            }
            // A manager only ever sees their own direct reports.
            managerId = actor.employeeId();
            includeOffboarded = false;
        }

        List<Specification<Employee>> specs = new ArrayList<>();
        if (!includeOffboarded && status != EmploymentStatus.TERMINATED) {
            specs.add(EmployeeSpecifications.notDeleted());
        }
        if (departmentId != null) {
            specs.add(EmployeeSpecifications.inDepartment(departmentId));
        }
        if (status != null) {
            specs.add(EmployeeSpecifications.withStatus(status));
        }
        if (managerId != null) {
            specs.add(EmployeeSpecifications.managedBy(managerId));
        }
        if (search != null && !search.isBlank()) {
            specs.add(EmployeeSpecifications.matches(search));
        }
        return PageResponse.of(employeeRepository.findAll(Specification.allOf(specs), pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<DirectoryEntry> directory(String search, Pageable pageable) {
        List<Specification<Employee>> specs = new ArrayList<>();
        specs.add(EmployeeSpecifications.notDeleted());
        if (search != null && !search.isBlank()) {
            specs.add(EmployeeSpecifications.matches(search));
        }
        return PageResponse.of(employeeRepository.findAll(Specification.allOf(specs), pageable),
                mapper::toDirectoryEntry);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(AuthenticatedUser actor, Long id) {
        Employee employee = find(id);
        boolean privileged = isHrOrAdmin(actor);
        boolean self = id.equals(actor.employeeId());
        boolean directManager = employee.getManager() != null && employee.getManager().getId().equals(actor.employeeId());
        if (!privileged && !self && !directManager) {
            throw new AccessDeniedException("Access denied");
        }
        if (employee.isDeleted() && !privileged) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        return mapper.toResponse(employee);
    }

    /** Same visibility as everywhere else: HR/Admin, the employee and their direct manager see everything. */
    @Transactional(readOnly = true)
    public ProfileEmployee getForProfile(AuthenticatedUser actor, Long id) {
        Employee employee = find(id);
        boolean privileged = isHrOrAdmin(actor);
        boolean self = id.equals(actor.employeeId());
        boolean directManager = employee.getManager() != null && employee.getManager().getId().equals(actor.employeeId());
        if (employee.isDeleted() && !privileged) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        return new ProfileEmployee(mapper.toResponse(employee), privileged || self || directManager);
    }

    /** Current (not offboarded) people reporting directly to this employee, by name. */
    @Transactional(readOnly = true)
    public List<DirectoryEntry> directReports(Long managerId) {
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecifications.notDeleted(), EmployeeSpecifications.managedBy(managerId));
        return employeeRepository.findAll(spec, Sort.by("fullName")).stream().map(mapper::toDirectoryEntry).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> team(AuthenticatedUser actor, Long managerEmployeeId, Pageable pageable) {
        if (!isHrOrAdmin(actor)) {
            if (actor.role() != Role.MANAGER || !managerEmployeeId.equals(actor.employeeId())) {
                throw new AccessDeniedException("Access denied");
            }
        }
        find(managerEmployeeId);
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecifications.notDeleted(), EmployeeSpecifications.managedBy(managerEmployeeId));
        return PageResponse.of(employeeRepository.findAll(spec, pageable), mapper::toResponse);
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public EmployeeResponse create(AuthenticatedUser actor, EmployeeRequest request, String ip) {
        String email = request.email().trim().toLowerCase();
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An employee with this email already exists");
        }
        UserAccountRequest account = request.userAccount();
        if (account != null) {
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new ApiException(HttpStatus.CONFLICT, "A user account with this email already exists");
            }
            if (actor.role() != Role.SYSTEM_ADMIN && !HR_GRANTABLE_ROLES.contains(account.role())) {
                throw new AccessDeniedException("You are not allowed to assign the role " + account.role());
            }
        }

        Employee employee = new Employee();
        // Unique placeholder so the NOT NULL/UNIQUE column is satisfied until the id is known.
        employee.setEmployeeCode("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        apply(employee, request, email);
        employee = employeeRepository.save(employee);
        employee.setEmployeeCode(EmployeeCodeGenerator.fromSequence(employee.getId()));

        if (account != null) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(account.password()));
            user.setRole(account.role());
            user.setEmployeeId(employee.getId());
            userRepository.save(user);
            auditService.log(actor.userId(), "USER_CREATED", "USER", user.getId(), ip);
        }
        // Give the new joiner their leave balances for the current year (no-op until leave types exist).
        leaveBalanceService.initializeForEmployee(employee.getId(), LocalDate.now().getYear());
        auditService.log(actor.userId(), "EMPLOYEE_CREATED", ENTITY, employee.getId(), ip);
        return mapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse update(AuthenticatedUser actor, Long id, EmployeeRequest request, String ip) {
        if (request.userAccount() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Login accounts can only be created when onboarding");
        }
        Employee employee = find(id);
        if (employee.isDeleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee has been offboarded and can no longer be edited");
        }
        String email = request.email().trim().toLowerCase();
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "An employee with this email already exists");
        }
        apply(employee, request, email);
        auditService.log(actor.userId(), "EMPLOYEE_UPDATED", ENTITY, id, ip);
        return mapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateOwnProfile(AuthenticatedUser actor, Long id, SelfProfileRequest request, String ip) {
        if (actor.employeeId() == null || !actor.employeeId().equals(id)) {
            throw new AccessDeniedException("You can only edit your own profile");
        }
        if (!request.rejectedFields().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "These fields cannot be changed from your profile: " + String.join(", ", request.rejectedFields()));
        }
        Employee employee = find(id);
        if (employee.isDeleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee has been offboarded");
        }
        if (request.getPhone() != null) {
            employee.setPhone(blankToNull(request.getPhone()));
        }
        if (request.getAddress() != null) {
            employee.setAddress(blankToNull(request.getAddress()));
        }
        if (request.getEmergencyContact() != null) {
            employee.setEmergencyContact(blankToNull(request.getEmergencyContact()));
        }
        profileCompletion.afterOwnUpdate(employee);
        auditService.log(actor.userId(), "EMPLOYEE_PROFILE_UPDATED", ENTITY, id, ip);
        return mapper.toResponse(employee);
    }

    /** Soft delete: the row and its history stay; the linked login account is deactivated. */
    @Transactional
    public EmployeeResponse offboard(AuthenticatedUser actor, Long id, String ip) {
        if (id.equals(actor.employeeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot offboard yourself");
        }
        Employee employee = find(id);
        if (employee.isDeleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee is already offboarded");
        }
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        employee.setDeleted(true);
        userRepository.findByEmployeeId(id).ifPresent(user -> user.setActive(false));
        auditService.log(actor.userId(), "EMPLOYEE_OFFBOARDED", ENTITY, id, ip);
        return mapper.toResponse(employee);
    }

    /** Admin/HR: move an employee to another department and/or manager, blocking reporting loops. */
    @Transactional
    public EmployeeResponse reassign(AuthenticatedUser actor, Long id, ReassignRequest request, String ip) {
        if (request.departmentId() == null && request.managerId() == null && !request.shouldRemoveManager()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to change: provide a department and/or a manager");
        }
        if (request.shouldRemoveManager() && request.managerId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose either a new manager or 'remove manager', not both");
        }
        Employee employee = find(id);
        if (employee.isDeleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee has been offboarded and can no longer be reassigned");
        }
        if (request.departmentId() != null) {
            employee.setDepartment(departmentRepository.findById(request.departmentId()).filter(Department::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Department not found")));
        }
        if (request.shouldRemoveManager()) {
            employee.setManager(null);
        } else if (request.managerId() != null) {
            Employee manager = employeeRepository.findById(request.managerId()).filter(m -> !m.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Manager not found"));
            assertNoManagerCycle(employee, manager);
            employee.setManager(manager);
        }
        auditService.log(actor.userId(), "EMPLOYEE_REASSIGNED", ENTITY, id, ip);
        return mapper.toResponse(employee);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isHrOrAdmin(AuthenticatedUser actor) {
        return actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
    }

    private Employee find(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private void apply(Employee employee, EmployeeRequest r, String normalisedEmail) {
        if (r.employmentStatus() == EmploymentStatus.TERMINATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the offboard action to terminate an employee");
        }
        validateDates(r.dateOfBirth(), r.joiningDate());

        Department department = departmentRepository.findById(r.departmentId()).filter(Department::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Department not found"));
        Designation designation = null;
        if (r.designationId() != null) {
            designation = designationRepository.findById(r.designationId()).filter(Designation::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Designation not found"));
        }
        Employee manager = null;
        if (r.managerId() != null) {
            manager = employeeRepository.findById(r.managerId()).filter(m -> !m.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Manager not found"));
            assertNoManagerCycle(employee, manager);
        }

        employee.setFullName(r.fullName().trim());
        employee.setEmail(normalisedEmail);
        employee.setPhone(blankToNull(r.phone()));
        employee.setAddress(blankToNull(r.address()));
        employee.setEmergencyContact(blankToNull(r.emergencyContact()));
        employee.setDateOfBirth(r.dateOfBirth());
        employee.setJoiningDate(r.joiningDate());
        employee.setJobTitle(r.jobTitle().trim());
        employee.setEmploymentStatus(r.employmentStatus());
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setManager(manager);
    }

    private static void validateDates(LocalDate dateOfBirth, LocalDate joiningDate) {
        LocalDate today = LocalDate.now();
        if (joiningDate.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Joining date cannot be in the future");
        }
        if (dateOfBirth != null) {
            if (dateOfBirth.isAfter(today.minusYears(16)) || dateOfBirth.isBefore(today.minusYears(100))) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Date of birth is not plausible (employee must be between 16 and 100 years old)");
            }
            if (joiningDate.isBefore(dateOfBirth.plusYears(16))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Joining date is before the employee turned 16");
            }
        }
    }

    /** An employee cannot report to themselves or to anyone in their own reporting chain. */
    private static void assertNoManagerCycle(Employee employee, Employee newManager) {
        if (employee.getId() == null) {
            return;
        }
        Employee current = newManager;
        for (int i = 0; current != null && i < MAX_MANAGER_CHAIN; i++) {
            if (employee.getId().equals(current.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Manager selection would create a reporting loop");
            }
            current = current.getManager();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
