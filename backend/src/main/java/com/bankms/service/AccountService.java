package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.account.AccountLookupResponse;
import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.account.AccountStatusRequest;
import com.bankms.entity.Account;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.Branch;
import com.bankms.entity.Customer;
import com.bankms.entity.NotificationType;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.mapper.AccountMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final NumberGenerator numbers;
    private final RateService rates;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final AccountMapper mapper;
    private final Clock clock;

    /** Creates an empty ACTIVE account. Used by registration, onboarding, self-service and FD booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account createAccount(Customer customer, AccountType type, Branch branch) {
        Account account = new Account();
        account.setCustomer(customer);
        account.setBranch(branch);
        account.setAccountType(type);
        account.setAccountNumber(numbers.nextAccountNumber(branch));
        account.setOpenedAt(Instant.now(clock));
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listForCustomer(Long customerId) {
        return accounts.findByCustomerIdOrderByOpenedAtAsc(customerId).stream().map(this::toResponse).toList();
    }

    /** Caller has already been authorised (owner or staff) by @PreAuthorize on the controller. */
    @Transactional(readOnly = true)
    public AccountResponse get(Long accountId) {
        return accounts.findWithOwnerById(accountId).map(this::toResponse)
                .orElseThrow(() -> BankException.notFound("Account", accountId));
    }

    @Audited(action = AuditAction.ACCOUNT_OPENED, entityType = "ACCOUNT", entityId = "#result.accountNumber",
            details = "'type=' + #type")
    @Transactional
    public AccountResponse open(Long customerId, AccountType type) {
        Customer customer = customers.findDetailedById(customerId)
                .orElseThrow(() -> BankException.notFound("Customer", customerId));
        if (!customer.isKycVerified()) {
            throw new BankException(ErrorCode.KYC_NOT_VERIFIED, "New accounts can be opened once your KYC is verified");
        }
        if (type == AccountType.FIXED_DEPOSIT) {
            throw BankException.rule("Fixed deposits are opened from the Fixed Deposits section");
        }
        Account account = createAccount(customer, type, customer.getHomeBranch());
        notifications.notify(customer.getUser(), NotificationType.ACCOUNT, "New account opened",
                "Your %s account %s is ready to use.".formatted(label(type), account.getAccountNumber()));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(String accountNumber) {
        return accounts.findWithOwnerByAccountNumber(accountNumber).map(mapper::toLookup)
                .orElseThrow(() -> BankException.notFound("Account", accountNumber));
    }

    /**
     * Freeze / unfreeze / close. Takes the same row lock as transfers, so a freeze cannot interleave
     * with a transfer that is halfway through its checks.
     */
    @Audited(action = AuditAction.ACCOUNT_STATUS_CHANGED, entityType = "ACCOUNT", entityId = "#accountId",
            details = "'status=' + #request.status + ', reason=' + #request.reason")
    @Transactional
    public AccountResponse changeStatus(Long accountId, AccountStatusRequest request) {
        Account account = ledger.lock(accountId);
        AccountStatus target = request.status();
        if (account.getStatus() == target) {
            throw BankException.rule("Account is already " + target.name().toLowerCase());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED, "A closed account cannot be reopened");
        }
        if (target == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw BankException.rule("Only a zero-balance account can be closed; current balance is " + account.getBalance());
        }
        if (target == AccountStatus.CLOSED && account.isFixedDeposit()) {
            throw BankException.rule("Fixed deposits close automatically on maturity");
        }
        account.changeStatus(target, request.reason(), Instant.now(clock));
        notifications.notifyAndEmail(account.getCustomer().getUser(), NotificationType.ACCOUNT,
                "Account " + target.name().toLowerCase(),
                "Your account %s is now %s. Reason: %s".formatted(
                        Masking.accountNumber(account.getAccountNumber()), target.name().toLowerCase(), request.reason()));
        return toResponse(account);
    }

    public AccountResponse toResponse(Account account) {
        return mapper.toResponse(account, rates.policy(account.getAccountType()));
    }

    static String label(AccountType type) {
        return switch (type) {
            case SAVINGS -> "savings";
            case CURRENT -> "current";
            case FIXED_DEPOSIT -> "fixed deposit";
        };
    }
}
