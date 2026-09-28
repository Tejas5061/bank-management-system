package com.bankms.audit;

import lombok.Builder;

@Builder
public record AuditEntry(
        Long actorId,
        String actorEmail,
        String actorRole,
        AuditAction action,
        String entityType,
        String entityId,
        AuditOutcome outcome,
        String details,
        String ipAddress) {
}
