package com.staffsuite.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.staffsuite.entity.Employee;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Works out which user accounts should be told about something. */
@Component
@RequiredArgsConstructor
public class NotificationRecipients {

    private final UserRepository userRepository;

    /** The login of an active employee, if they have one. */
    public Optional<Long> userOf(Long employeeId) {
        return userRepository.findByEmployeeId(employeeId).filter(User::isActive).map(User::getId);
    }

    /**
     * Who can decide a request raised by {@code requester}: their direct manager, but only if that
     * manager has an active login that is actually allowed to approve (a manager whose login has the plain
     * EMPLOYEE role cannot). With no such manager it falls back to HR. The requester is never included.
     */
    public List<Long> approversFor(Employee requester) {
        Long requesterUserId = userOf(requester.getId()).orElse(null);
        Employee manager = requester.getManager();
        if (manager != null) {
            Optional<User> managerUser = userRepository.findByEmployeeId(manager.getId())
                    .filter(User::isActive).filter(u -> u.getRole() != Role.EMPLOYEE);
            if (managerUser.isPresent() && !managerUser.get().getId().equals(requesterUserId)) {
                return List.of(managerUser.get().getId());
            }
        }
        return hrUsers().stream().filter(id -> !id.equals(requesterUserId)).toList();
    }

    /** Active HR administrators; if the organisation has none, the system administrators instead. */
    public List<Long> hrUsers() {
        List<Long> hr = userRepository.findByRoleAndActiveTrueOrderByIdAsc(Role.HR_ADMIN).stream().map(User::getId).toList();
        if (!hr.isEmpty()) {
            return hr;
        }
        return userRepository.findByRoleAndActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN).stream().map(User::getId).toList();
    }
}
