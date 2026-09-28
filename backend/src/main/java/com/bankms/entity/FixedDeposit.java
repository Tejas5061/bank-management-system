package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fixed_deposits")
@Getter
@Setter
@NoArgsConstructor
public class FixedDeposit extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /** Where principal plus interest is credited on maturity. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_account_id", nullable = false)
    private Account payoutAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    /** Locked in at booking time; later rate changes do not affect an existing FD. */
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "maturity_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maturityAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FixedDepositStatus status = FixedDepositStatus.ACTIVE;

    @Column(name = "matured_at")
    private Instant maturedAt;
}
