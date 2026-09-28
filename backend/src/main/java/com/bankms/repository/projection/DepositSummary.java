package com.bankms.repository.projection;

import com.bankms.entity.AccountType;

import java.math.BigDecimal;

public interface DepositSummary {

    AccountType getAccountType();

    long getAccounts();

    BigDecimal getBalance();
}
