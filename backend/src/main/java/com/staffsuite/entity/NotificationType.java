package com.staffsuite.entity;

/** What a notification is about. The frontend uses the type to decide where clicking it should go. */
public enum NotificationType {
    LEAVE_REQUESTED,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    ATTENDANCE_CORRECTION_REQUESTED,
    ATTENDANCE_CORRECTION_APPROVED,
    ATTENDANCE_CORRECTION_REJECTED,
    DOCUMENT_EXPIRING,
    PROFILE_COMPLETED
}
