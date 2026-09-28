package com.bankms.scheduler;

import com.bankms.config.AppProperties;
import com.bankms.repository.IdempotencyRecordRepository;
import com.bankms.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Deletes idempotency keys past their retention window and expired refresh tokens. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousekeepingScheduler {

    private final IdempotencyRecordRepository idempotencyRecords;
    private final RefreshTokenService refreshTokens;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${app.scheduling.housekeeping-cron}", zone = "${app.bank.timezone}")
    public void purge() {
        Instant cutoff = Instant.now(clock).minus(properties.transfer().idempotencyTtl());
        int keys = idempotencyRecords.deleteOlderThan(cutoff);
        int tokens = refreshTokens.purgeExpired();
        if (keys + tokens > 0) {
            log.info("Housekeeping removed {} idempotency keys and {} expired refresh tokens", keys, tokens);
        }
    }
}
