package com.staffsuite.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.config.AppProperties;
import com.staffsuite.entity.EmployeeDocument;
import com.staffsuite.entity.NotificationType;
import com.staffsuite.repository.EmployeeDocumentRepository;
import com.staffsuite.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tells HR when a document's expiry date has come within the reminder window (the same configurable
 * window as the Expiring Documents list). Safe to call as often as you like: a document is announced once
 * per window it enters, so running it twice, or daily, never repeats a reminder.
 */
@Service
@RequiredArgsConstructor
public class DocumentReminderService {

    private final EmployeeDocumentRepository documentRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final NotificationRecipients recipients;
    private final AppProperties props;
    private final Clock clock;

    /** @return how many notifications were created by this run */
    @Transactional
    public int sendExpiryReminders() {
        int window = props.documents().expiryWindowDays();
        LocalDate today = LocalDate.now(clock);
        List<EmployeeDocument> expiring = documentRepository.findExpiringBetween(today, today.plusDays(window));
        if (expiring.isEmpty()) {
            return 0;
        }
        List<Long> hr = recipients.hrUsers();
        int created = 0;
        for (EmployeeDocument document : expiring) {
            // Anything already sent since this document's window opened counts as "already reminded".
            // If the expiry date is later moved, the window moves with it and a fresh reminder is due.
            Instant windowOpened = document.getExpiryDate().minusDays(window).atStartOfDay(clock.getZone()).toInstant();
            String message = "Document expiring: " + document.getCategory().getName() + " ("
                    + document.getFileName() + ") of " + document.getEmployee().getFullName() + " expires on "
                    + document.getExpiryDate() + " (" + daysLeft(today, document.getExpiryDate()) + ")";
            for (Long recipient : hr) {
                if (!notificationRepository.existsByRecipientIdAndTypeAndReferenceIdAndCreatedAtGreaterThanEqual(
                        recipient, NotificationType.DOCUMENT_EXPIRING, document.getId(), windowOpened)) {
                    notificationService.notify(recipient, NotificationType.DOCUMENT_EXPIRING, message, document.getId());
                    created++;
                }
            }
        }
        return created;
    }

    private static String daysLeft(LocalDate today, LocalDate expiry) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
        return days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
    }
}
