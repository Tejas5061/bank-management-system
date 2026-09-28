package com.bankms.scheduler;

import com.bankms.service.InterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Credits last month's savings interest early on the 1st. Re-runs are harmless (see
 * InterestService). With several instances, add ShedLock so only one of them does the work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestScheduler {

    private final InterestService interestService;
    private final Clock clock;

    @Scheduled(cron = "${app.scheduling.interest-cron}", zone = "${app.bank.timezone}")
    public void creditMonthlyInterest() {
        YearMonth previous = YearMonth.now(clock).minusMonths(1);
        log.info("Scheduled interest posting for {}", previous);
        interestService.postMonthlyInterest(previous);
    }
}
