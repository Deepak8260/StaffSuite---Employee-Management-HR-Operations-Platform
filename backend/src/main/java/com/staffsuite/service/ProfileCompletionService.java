package com.staffsuite.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staffsuite.dto.EmployeeDtos.ProfileCompletion;
import com.staffsuite.dto.EmployeeDtos.ProfileCompletionItem;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.NotificationType;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.EmployeeDocumentRepository;
import com.staffsuite.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * How complete an employee's profile is, and the one-time "profile completed" notification.
 *
 * <p>Only things the employee can provide themself count: a photo, phone, address, emergency contact and at least one
 * document. Fields HR owns (date of birth, designation, ...) are not held against them. Each item is worth the same share.
 */
@Service
@RequiredArgsConstructor
public class ProfileCompletionService {

    private final NotificationService notificationService;
    private final NotificationRecipients recipients;
    private final UserRepository userRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final Clock clock;

    /** The checklist for an employee's current data. */
    public static ProfileCompletion of(boolean hasPhoto, String phone, String address, String emergencyContact, boolean hasDocument) {
        List<ProfileCompletionItem> items = new ArrayList<>();
        items.add(new ProfileCompletionItem("photo", "Profile photo", hasPhoto));
        items.add(new ProfileCompletionItem("phone", "Phone number", filled(phone)));
        items.add(new ProfileCompletionItem("address", "Home address", filled(address)));
        items.add(new ProfileCompletionItem("emergencyContact", "Emergency contact", filled(emergencyContact)));
        items.add(new ProfileCompletionItem("documents", "At least one document uploaded", hasDocument));
        long done = items.stream().filter(ProfileCompletionItem::done).count();
        return new ProfileCompletion((int) Math.round(done * 100.0 / items.size()), done == items.size(), items);
    }

    /** For the profile page, from the values it already holds. */
    public ProfileCompletion of(Long employeeId, boolean hasPhoto, String phone, String address, String emergencyContact) {
        return of(hasPhoto, phone, address, emergencyContact, documentRepository.existsByEmployee_Id(employeeId));
    }

    public ProfileCompletion of(Employee e) {
        return of(e.getId(), e.getProfilePhotoUrl() != null, e.getPhone(), e.getAddress(), e.getEmergencyContact());
    }

    /**
     * Call after the employee changed their own profile data (inside that transaction). The first time the profile
     * reaches 100% it is stamped and the manager (if any) and the system administrators are told, once.
     */
    public void afterOwnUpdate(Employee employee) {
        if (employee.getProfileCompletedAt() != null || !of(employee).complete()) {
            return;
        }
        employee.setProfileCompletedAt(Instant.now(clock));

        List<Long> to = new ArrayList<>();
        if (employee.getManager() != null) {
            recipients.userOf(employee.getManager().getId()).ifPresent(to::add);
        }
        userRepository.findByRoleAndActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN).forEach(u -> to.add(u.getId()));
        Long ownUser = userRepository.findByEmployeeId(employee.getId()).map(User::getId).orElse(null);
        to.removeIf(id -> id.equals(ownUser));
        notificationService.notifyAll(to, NotificationType.PROFILE_COMPLETED,
                employee.getFullName() + " has completed their profile.", employee.getId());
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
