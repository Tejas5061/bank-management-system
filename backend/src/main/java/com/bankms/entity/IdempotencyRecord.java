package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord extends CreatedAtEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 80, updatable = false)
    private String endpoint;

    /** SHA-256 of the request body, so a reused key with a different payload is rejected. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    public IdempotencyRecord(Long userId, String idempotencyKey, String endpoint, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
    }

    public boolean isCompleted() {
        return responseStatus != null;
    }
}
