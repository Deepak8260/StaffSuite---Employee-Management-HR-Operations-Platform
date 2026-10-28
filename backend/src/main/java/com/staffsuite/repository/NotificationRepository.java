package com.staffsuite.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.Notification;
import com.staffsuite.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadFalse(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    /** True if this person already got a notification of this type about this item at or after {@code since}. */
    boolean existsByRecipientIdAndTypeAndReferenceIdAndCreatedAtGreaterThanEqual(Long recipientId,
            NotificationType type, Long referenceId, Instant since);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now "
            + "where n.recipientId = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
