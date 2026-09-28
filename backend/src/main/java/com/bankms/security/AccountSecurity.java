package com.bankms.security;

import com.bankms.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Referenced from SpEL, e.g. {@code @PreAuthorize("@accountSecurity.canView(#accountId)")}.
 * Staff may view any account; a customer only their own. Keeping the rule here (not in each
 * controller) means there is exactly one place to get it right.
 */
@Component("accountSecurity")
@RequiredArgsConstructor
public class AccountSecurity {

    private final AccountRepository accounts;

    public boolean canView(Long accountId) {
        return CurrentUser.find()
                .map(user -> user.isStaff() || accounts.isOwnedByUser(accountId, user.id()))
                .orElse(false);
    }
}
