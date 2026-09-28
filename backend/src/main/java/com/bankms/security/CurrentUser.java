package com.bankms.security;

import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the principal for services that are not handed it explicitly. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return find().orElseThrow(() -> new BankException(ErrorCode.UNAUTHORIZED));
    }
}
