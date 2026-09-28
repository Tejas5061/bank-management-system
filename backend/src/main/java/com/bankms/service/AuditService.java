package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.AuditEntry;
import com.bankms.audit.AuditOutcome;
import com.bankms.dto.admin.AuditLogResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.entity.AuditLog;
import com.bankms.mapper.AdminMapper;
import com.bankms.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository auditLogs;
    private final AdminMapper mapper;

    /** Own transaction: the audit row survives even when the audited operation rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        AuditLog log = new AuditLog();
        log.setActorId(entry.actorId());
        log.setActorEmail(truncate(entry.actorEmail(), 120));
        log.setActorRole(entry.actorRole());
        log.setAction(entry.action());
        log.setEntityType(entry.entityType());
        log.setEntityId(truncate(entry.entityId(), 120));
        log.setOutcome(entry.outcome());
        log.setDetails(truncate(entry.details(), MAX_DETAILS));
        log.setIpAddress(truncate(entry.ipAddress(), 45));
        auditLogs.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action, AuditOutcome outcome, String actor,
                                                 Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (StringUtils.hasText(actor)) {
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actor.toLowerCase().trim() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(auditLogs.findAll(spec, pageable), mapper::toAuditLog);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
