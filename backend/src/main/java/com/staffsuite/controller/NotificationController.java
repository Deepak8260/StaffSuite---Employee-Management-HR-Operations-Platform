package com.staffsuite.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.NotificationDtos.MarkAllReadResponse;
import com.staffsuite.dto.NotificationDtos.NotificationResponse;
import com.staffsuite.dto.NotificationDtos.PendingApprovalsResponse;
import com.staffsuite.dto.NotificationDtos.UnreadCountResponse;
import com.staffsuite.dto.PageResponse;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.NotificationInboxService;

import lombok.RequiredArgsConstructor;

/** Every endpoint works on the caller's own notifications only; there is no way to name another user. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationInboxService inbox;

    /** Newest first. */
    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return inbox.list(actor, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser actor) {
        return inbox.unreadCount(actor);
    }

    @GetMapping("/pending-approvals")
    public PendingApprovalsResponse pendingApprovals(@AuthenticationPrincipal AuthenticatedUser actor) {
        return inbox.pendingApprovals(actor);
    }

    @PutMapping("/read-all")
    public MarkAllReadResponse readAll(@AuthenticationPrincipal AuthenticatedUser actor) {
        return inbox.markAllRead(actor);
    }

    @PutMapping("/{id}/read")
    public NotificationResponse read(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return inbox.markRead(actor, id);
    }
}
