package com.staffsuite.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
