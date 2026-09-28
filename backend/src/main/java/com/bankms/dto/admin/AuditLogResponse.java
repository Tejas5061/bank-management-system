package com.bankms.dto.admin;

import com.bankms.audit.AuditAction;
import com.bankms.audit.AuditOutcome;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorEmail,
        String actorRole,
        AuditAction action,
        String entityType,
        String entityId,
        AuditOutcome outcome,
        String details,
        String ipAddress,
        Instant createdAt) {
}
