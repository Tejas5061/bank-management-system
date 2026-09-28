package com.bankms.exception;

import com.bankms.entity.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A money movement that was valid input but was declined by a banking rule (insufficient funds,
 * frozen account, daily limit...). Carries enough context for the caller to record a FAILED ledger
 * row after the declining transaction has rolled back.
 */
@Getter
public class TransactionDeclinedException extends BankException {

    private final Long accountId;
    private final TransactionType attemptedType;
    private final BigDecimal amount;
    private final BigDecimal balanceAtDecline;

    public TransactionDeclinedException(ErrorCode errorCode, String message, Long accountId,
                                        TransactionType attemptedType, BigDecimal amount,
                                        BigDecimal balanceAtDecline) {
        super(errorCode, message);
        this.accountId = accountId;
        this.attemptedType = attemptedType;
        this.amount = amount;
        this.balanceAtDecline = balanceAtDecline;
    }
}
