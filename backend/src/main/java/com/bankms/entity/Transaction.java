package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One ledger line for one account. Immutable: corrections are made with new, compensating
 * entries, never by editing history.
 */
@Entity
@Immutable
@Table(name = "transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Transaction extends CreatedAtEntity {

    /** Shared by both legs of a transfer. */
    @Column(name = "reference_number", nullable = false, length = 24)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private TransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionChannel channel;

    @Column
    private String description;

    @Column(name = "counterparty_account", length = 20)
    private String counterpartyAccount;

    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    @Column(name = "counterparty_ifsc", length = 11)
    private String counterpartyIfsc;

    @Column(name = "failure_reason")
    private String failureReason;

    /** User who initiated the movement; null for scheduler-driven entries. */
    @Column(name = "initiated_by")
    private Long initiatedBy;

    /** Business date in the bank's time zone; drives statements, limits and interest. */
    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;
}
