package com.staffsuite.dto;

import java.time.Instant;

import com.staffsuite.entity.NotificationType;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(Long id, NotificationType type, String message, Long referenceId, boolean read,
            Instant readAt, Instant createdAt) {
    }

    public record UnreadCountResponse(long count) {
    }

    public record MarkAllReadResponse(int updated) {
    }

    /** Computed on demand (not stored): what is currently waiting for this person to decide. */
    public record PendingApprovalsResponse(long leaveRequests, long attendanceCorrections, long total) {
    }
}
