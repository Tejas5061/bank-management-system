package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "loan_installments")
@Getter
@Setter
@NoArgsConstructor
public class LoanInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "emi_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "principal_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalComponent;

    @Column(name = "interest_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestComponent;

    /** Outstanding principal once this installment is paid. */
    @Column(name = "closing_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "transaction_ref", length = 24)
    private String transactionRef;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_failure_reason")
    private String lastFailureReason;

    public void markPaid(String transactionRef, Instant now) {
        this.status = InstallmentStatus.PAID;
        this.transactionRef = transactionRef;
        this.paidAt = now;
        this.attempts++;
        this.lastFailureReason = null;
    }

    public void markOverdue(String reason) {
        this.status = InstallmentStatus.OVERDUE;
        this.attempts++;
        this.lastFailureReason = reason;
    }
}
