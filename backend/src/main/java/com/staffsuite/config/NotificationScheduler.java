package com.staffsuite.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.staffsuite.service.DocumentReminderService;

import lombok.RequiredArgsConstructor;

/**
 * The only scheduled job: once a day, remind HR about documents entering their expiry window. It runs
 * inside the application (no external scheduler needed) and can be switched off with
 * NOTIFICATIONS_SCHEDULING=false.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "staffsuite.notifications.scheduling", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final DocumentReminderService reminders;

    @Scheduled(cron = "${staffsuite.notifications.reminder-cron}", zone = "${staffsuite.attendance.timezone}")
    public void sendDocumentExpiryReminders() {
        try {
            int created = reminders.sendExpiryReminders();
            log.info("Document expiry reminders: {} notification(s) created", created);
        } catch (RuntimeException e) {
            log.error("Document expiry reminder run failed", e);
        }
    }
}
