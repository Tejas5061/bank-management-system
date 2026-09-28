package com.bankms.mapper;

import com.bankms.dto.account.AccountLookupResponse;
import com.bankms.dto.account.AccountResponse;
import com.bankms.entity.Account;
import com.bankms.entity.AccountPolicy;
import com.bankms.util.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper
public interface AccountMapper {

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "accountNumber", source = "account.accountNumber")
    @Mapping(target = "accountType", source = "account.accountType")
    @Mapping(target = "status", source = "account.status")
    @Mapping(target = "statusReason", source = "account.statusReason")
    @Mapping(target = "balance", source = "account.balance")
    @Mapping(target = "availableBalance", expression = "java(availableBalance(account, policy))")
    @Mapping(target = "minimumBalance", expression = "java(minimumBalance(account, policy))")
    @Mapping(target = "interestRate", source = "policy.interestRate")
    @Mapping(target = "currency", source = "account.currency")
    @Mapping(target = "branchCode", source = "account.branch.code")
    @Mapping(target = "branchName", source = "account.branch.name")
    @Mapping(target = "ifsc", source = "account.branch.ifsc")
    @Mapping(target = "openedAt", source = "account.openedAt")
    @Mapping(target = "closedAt", source = "account.closedAt")
    AccountResponse toResponse(Account account, AccountPolicy policy);

    @Mapping(target = "accountId", source = "id")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerNumber", source = "customer.customerNumber")
    @Mapping(target = "holderName", source = "customer.user.fullName")
    @Mapping(target = "kycStatus", source = "customer.kycStatus")
    @Mapping(target = "branchCode", source = "branch.code")
    AccountLookupResponse toLookup(Account account);

    default BigDecimal minimumBalance(Account account, AccountPolicy policy) {
        return account.isFixedDeposit() ? Money.ZERO : policy.getMinimumBalance();
    }

    /** What the customer can actually move: nothing for an FD, balance minus minimum otherwise. */
    default BigDecimal availableBalance(Account account, AccountPolicy policy) {
        if (account.isFixedDeposit() || !account.isActive()) {
            return Money.ZERO;
        }
        BigDecimal available = account.getBalance().subtract(policy.getMinimumBalance());
        return available.signum() > 0 ? available : Money.ZERO;
    }
}
