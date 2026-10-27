package com.staffsuite.service;

import org.springframework.stereotype.Service;

import com.staffsuite.entity.AuditLog;
import com.staffsuite.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Long userId, String action, String entityType, Long entityId, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);
    }
}
