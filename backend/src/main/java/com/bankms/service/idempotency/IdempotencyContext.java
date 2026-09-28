package com.bankms.service.idempotency;

/** Identifies one logical request: who sent it, the key they chose, and a hash of what they asked for. */
public record IdempotencyContext(Long userId, String key, String endpoint, String requestHash) {
}
