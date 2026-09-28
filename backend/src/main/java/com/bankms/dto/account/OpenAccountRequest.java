package com.bankms.dto.account;

import com.bankms.entity.AccountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Fixed deposits are opened through the FD endpoint, which also funds them. */
public record OpenAccountRequest(@NotNull AccountType accountType) {

    @AssertTrue(message = "accountType must be SAVINGS or CURRENT")
    public boolean isOperativeAccountType() {
        return accountType == null || accountType != AccountType.FIXED_DEPOSIT;
    }
}
