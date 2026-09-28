package com.bankms.service.idempotency;

import com.bankms.entity.IdempotencyRecord;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.repository.IdempotencyRecordRepository;
import com.bankms.util.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes money-moving POSTs safe to retry (double clicks, client timeouts, flaky mobile networks).
 * <ol>
 *   <li>Fast path: if the key was already used, replay the stored response (or reject if the body
 *       differs).</li>
 *   <li>Otherwise run the operation. Its FIRST statement, inside its own DB transaction, inserts the
 *       key under a UNIQUE(user_id, key) constraint; the response is stored in the same transaction.
 *       So "money moved" and "key recorded" commit or roll back together.</li>
 *   <li>If two identical requests race past step 1, the second INSERT blocks on the unique index
 *       until the first commits, then fails with a duplicate key. We catch that and replay the
 *       winner's stored response. Exactly one transfer happens.</li>
 * </ol>
 * A declined request (e.g. insufficient funds) rolls back its key too, so the client may retry
 * with the same key once the problem is fixed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository records;
    private final ObjectMapper objectMapper;

    public IdempotencyContext context(Long userId, String key, String endpoint, Object request) {
        return new IdempotencyContext(userId, key, endpoint, Hashing.sha256Hex(endpoint + '|' + toJson(request)));
    }

    public <T> Idempotent<T> execute(IdempotencyContext context, Class<T> responseType, Supplier<T> operation) {
        Optional<IdempotencyRecord> existing = records.findByUserIdAndIdempotencyKey(context.userId(), context.key());
        if (existing.isPresent()) {
            return replay(existing.get(), context, responseType);
        }
        try {
            return Idempotent.fresh(operation.get());
        } catch (DataIntegrityViolationException e) {
            IdempotencyRecord winner = records.findByUserIdAndIdempotencyKey(context.userId(), context.key())
                    .orElseThrow(() -> e); // not our unique key: some other constraint failed
            log.info("Concurrent duplicate request for idempotency key {} resolved by replay", context.key());
            return replay(winner, context, responseType);
        }
    }

    /** Must be the first write of the operation's transaction (see class comment). */
    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyRecord reserve(IdempotencyContext context) {
        return records.saveAndFlush(new IdempotencyRecord(
                context.userId(), context.key(), context.endpoint(), context.requestHash()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(IdempotencyRecord record, int httpStatus, Object response) {
        record.setResponseStatus(httpStatus);
        record.setResponseBody(toJson(response));
    }

    private <T> Idempotent<T> replay(IdempotencyRecord record, IdempotencyContext context, Class<T> responseType) {
        if (!record.getRequestHash().equals(context.requestHash())) {
            throw new BankException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!record.isCompleted()) {
            throw new BankException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        try {
            return Idempotent.replay(objectMapper.readValue(record.getResponseBody(), responseType));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }
}
