package com.bankms.dto.account;

import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.KycStatus;

import java.math.BigDecimal;

/** What a teller sees after keying in an account number at the cash desk. */
public record AccountLookupResponse(
        Long accountId,
        String accountNumber,
        AccountType accountType,
        AccountStatus status,
        Long customerId,
        String customerNumber,
        String holderName,
        KycStatus kycStatus,
        BigDecimal balance,
        String branchCode) {
}
