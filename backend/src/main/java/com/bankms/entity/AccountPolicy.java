package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Admin-managed rules per account type: interest rate, minimum balance, daily transfer limit. */
@Entity
@Table(name = "account_policies")
@Getter
@Setter
@NoArgsConstructor
public class AccountPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private AccountType accountType;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    /** For fixed deposits this is the minimum principal. */
    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumBalance;

    @Column(name = "daily_transfer_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
