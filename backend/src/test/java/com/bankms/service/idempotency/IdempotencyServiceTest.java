package com.bankms.service.idempotency;

import com.bankms.entity.IdempotencyRecord;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository records;

    private IdempotencyService service;
    private final AtomicInteger executions = new AtomicInteger();

    record Receipt(String reference) {
    }

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(records, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void firstRequestRunsTheOperation() {
        IdempotencyContext context = service.context(7L, "key-00000001", "POST /x", Map.of("amount", 10));
        when(records.findByUserIdAndIdempotencyKey(7L, "key-00000001")).thenReturn(Optional.empty());

        Idempotent<Receipt> result = service.execute(context, Receipt.class, this::run);

        assertThat(result.replayed()).isFalse();
        assertThat(executions).hasValue(1);
    }

    @Test
    void repeatedKeyReplaysTheStoredResponseWithoutRunningAgain() {
        IdempotencyContext context = service.context(7L, "key-00000001", "POST /x", Map.of("amount", 10));
        when(records.findByUserIdAndIdempotencyKey(7L, "key-00000001"))
                .thenReturn(Optional.of(completed(context, "{\"reference\":\"TXN1\"}")));

        Idempotent<Receipt> result = service.execute(context, Receipt.class, this::run);

        assertThat(result.replayed()).isTrue();
        assertThat(result.body().reference()).isEqualTo("TXN1");
        assertThat(executions).hasValue(0);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() {
        IdempotencyContext original = service.context(7L, "key-00000001", "POST /x", Map.of("amount", 10));
        IdempotencyContext tampered = service.context(7L, "key-00000001", "POST /x", Map.of("amount", 99));
        when(records.findByUserIdAndIdempotencyKey(7L, "key-00000001"))
                .thenReturn(Optional.of(completed(original, "{\"reference\":\"TXN1\"}")));

        assertThatThrownBy(() -> service.execute(tampered, Receipt.class, this::run))
                .isInstanceOf(BankException.class)
                .extracting(e -> ((BankException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(executions).hasValue(0);
    }

    @Test
    void losingAConcurrentRaceReplaysTheWinnersResponse() {
        IdempotencyContext context = service.context(7L, "key-00000001", "POST /x", Map.of("amount", 10));
        when(records.findByUserIdAndIdempotencyKey(7L, "key-00000001"))
                .thenReturn(Optional.empty())                                        // fast-path check
                .thenReturn(Optional.of(completed(context, "{\"reference\":\"WIN\"}"))); // after the unique-key clash

        Idempotent<Receipt> result = service.execute(context, Receipt.class, () -> {
            throw new DuplicateKeyException("uk_idempotency_user_key");
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.body().reference()).isEqualTo("WIN");
    }

    private Receipt run() {
        executions.incrementAndGet();
        return new Receipt("TXN-NEW");
    }

    private static IdempotencyRecord completed(IdempotencyContext context, String body) {
        IdempotencyRecord record = new IdempotencyRecord(context.userId(), context.key(), context.endpoint(), context.requestHash());
        record.setResponseStatus(201);
        record.setResponseBody(body);
        return record;
    }
}
