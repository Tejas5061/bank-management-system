package com.bankms.scheduler;

import com.bankms.service.FixedDepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays out fixed deposits that matured today (or earlier, if a run was missed). */
@Component
@RequiredArgsConstructor
public class FixedDepositMaturityScheduler {

    private final FixedDepositService fixedDepositService;

    @Scheduled(cron = "${app.scheduling.fd-maturity-cron}", zone = "${app.bank.timezone}")
    public void payOutMaturedDeposits() {
        fixedDepositService.processMaturities();
    }
}
