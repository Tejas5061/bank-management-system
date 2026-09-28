package com.bankms.entity;

import com.bankms.audit.AuditAction;
import com.bankms.audit.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends CreatedAtEntity {

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 120)
    private String actorEmail;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 120)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOutcome outcome;

    @Column(length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
