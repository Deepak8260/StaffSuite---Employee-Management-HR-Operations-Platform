package com.staffsuite.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.NotificationDtos.MarkAllReadResponse;
import com.staffsuite.dto.NotificationDtos.NotificationResponse;
import com.staffsuite.dto.NotificationDtos.PendingApprovalsResponse;
import com.staffsuite.dto.NotificationDtos.UnreadCountResponse;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.entity.CorrectionStatus;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.Notification;
import com.staffsuite.entity.Role;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceCorrectionSpecifications;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveRequestSpecifications;
import com.staffsuite.repository.NotificationRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/** Everything a signed-in user does with their own notifications. Nobody can ever reach anyone else's. */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceCorrectionRepository correctionRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(AuthenticatedUser actor, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalse(actor.userId(), pageable)
                : notificationRepository.findByRecipientId(actor.userId(), pageable);
        return PageResponse.of(page, NotificationInboxService::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(AuthenticatedUser actor) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(actor.userId()));
    }

    /** Someone else's notification is reported as not found, the same as one that does not exist. */
    @Transactional
    public NotificationResponse markRead(AuthenticatedUser actor, Long id) {
        Notification notification = notificationRepository.findByIdAndRecipientId(id, actor.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public MarkAllReadResponse markAllRead(AuthenticatedUser actor) {
        return new MarkAllReadResponse(notificationRepository.markAllRead(actor.userId(), Instant.now()));
    }

    /**
     * What is waiting on this person right now: pending leave requests and attendance corrections they are
     * allowed to decide (a manager: their direct reports'; HR/Admin: everyone's except their own).
     * Computed live instead of storing a notification per item, so it can never pile up or go stale.
     */
    @Transactional(readOnly = true)
    public PendingApprovalsResponse pendingApprovals(AuthenticatedUser actor) {
        Specification<com.staffsuite.entity.LeaveRequest> leave;
        Specification<com.staffsuite.entity.AttendanceCorrection> correction;
        if (actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN) {
            leave = Specification.allOf(LeaveRequestSpecifications.withStatus(LeaveStatus.PENDING));
            correction = Specification.allOf(AttendanceCorrectionSpecifications.withStatus(CorrectionStatus.PENDING));
            if (actor.employeeId() != null) {
                leave = leave.and(LeaveRequestSpecifications.notOfEmployee(actor.employeeId()));
                correction = correction.and(AttendanceCorrectionSpecifications.notRequestedBy(actor.employeeId()));
            }
        } else if (actor.role() == Role.MANAGER && actor.employeeId() != null) {
            leave = Specification.allOf(LeaveRequestSpecifications.withStatus(LeaveStatus.PENDING),
                    LeaveRequestSpecifications.ofTeamOf(actor.employeeId()));
            correction = Specification.allOf(AttendanceCorrectionSpecifications.withStatus(CorrectionStatus.PENDING),
                    AttendanceCorrectionSpecifications.ofTeamOf(actor.employeeId()));
        } else {
            return new PendingApprovalsResponse(0, 0, 0);
        }
        long leaves = leaveRequestRepository.count(leave);
        long corrections = correctionRepository.count(correction);
        return new PendingApprovalsResponse(leaves, corrections, leaves + corrections);
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getReferenceId(), n.isRead(),
                n.getReadAt(), n.getCreatedAt());
    }
}
