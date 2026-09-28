package com.bankms.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
public class Loan extends BaseEntity {

    @Column(name = "loan_number", nullable = false, length = 20, updatable = false)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    /** Disbursement goes here and EMIs are auto-debited from here. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 20, updatable = false)
    private LoanType loanType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "emi_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(nullable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.PENDING;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * Optimistic locking: if two officers act on the same application at once, the second commit
     * fails with a conflict instead of approving (and disbursing) twice.
     */
    @Version
    private long version;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<LoanInstallment> installments = new ArrayList<>();

    public void addInstallment(LoanInstallment installment) {
        installment.setLoan(this);
        installments.add(installment);
    }

    public void review(LoanStatus decision, User reviewer, String remarks, Instant now) {
        this.status = decision;
        this.reviewedBy = reviewer;
        this.reviewRemarks = remarks;
        this.reviewedAt = now;
    }
}
