package com.staffsuite.service;

import java.util.Collection;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.entity.Notification;
import com.staffsuite.entity.NotificationType;
import com.staffsuite.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * The one place other services call to tell someone something. In-app only: there is deliberately no
 * email (or any other) delivery here.
 *
 * <p>It joins the caller's transaction, so if the business action is rolled back its notification is too.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_MESSAGE = 1000;

    private final NotificationRepository notificationRepository;

    @Transactional
    public void notify(Long recipientUserId, NotificationType type, String message, Long referenceId) {
        if (recipientUserId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientUserId);
        notification.setType(type);
        notification.setMessage(message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE - 1) + "…" : message);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);
    }

    /** Same message to several people; each distinct recipient gets exactly one. */
    @Transactional
    public void notifyAll(Collection<Long> recipientUserIds, NotificationType type, String message, Long referenceId) {
        recipientUserIds.stream().distinct().forEach(id -> notify(id, type, message, referenceId));
    }
}
