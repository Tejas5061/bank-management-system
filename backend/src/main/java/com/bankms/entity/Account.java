package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, length = 20, updatable = false)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20, updatable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * Money paths take a pessimistic row lock, so this version never fires there. It exists so that
     * any code path that forgets to lock fails with an optimistic-lock error instead of silently
     * losing an update.
     */
    @Version
    private long version;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isFixedDeposit() {
        return accountType == AccountType.FIXED_DEPOSIT;
    }

    /**
     * Invariant guard only. Business rules (minimum balance, limits) are enforced by the services
     * before calling this; the database CHECK constraint is the final backstop.
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Debit of " + amount + " would overdraw account " + accountNumber);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void changeStatus(AccountStatus newStatus, String reason, Instant now) {
        this.status = newStatus;
        this.statusReason = reason;
        if (newStatus == AccountStatus.CLOSED) {
            this.closedAt = now;
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
