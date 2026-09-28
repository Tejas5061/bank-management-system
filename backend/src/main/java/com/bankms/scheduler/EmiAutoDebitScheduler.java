package com.bankms.scheduler;

import com.bankms.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Collects due and overdue EMIs every morning; unpaid ones are retried the next day. */
@Component
@RequiredArgsConstructor
public class EmiAutoDebitScheduler {

    private final LoanService loanService;

    @Scheduled(cron = "${app.scheduling.emi-debit-cron}", zone = "${app.bank.timezone}")
    public void collectDueEmis() {
        loanService.collectDueInstallments();
    }
}
