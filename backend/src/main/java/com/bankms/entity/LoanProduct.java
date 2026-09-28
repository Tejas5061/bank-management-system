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

@Entity
@Table(name = "loan_products")
@Getter
@Setter
@NoArgsConstructor
public class LoanProduct {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", length = 20)
    private LoanType loanType;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "min_tenure_months", nullable = false)
    private int minTenureMonths;

    @Column(name = "max_tenure_months", nullable = false)
    private int maxTenureMonths;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
