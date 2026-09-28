package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "interest_postings")
@Getter
@Setter
@NoArgsConstructor
public class InterestPosting extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /** Calendar month in yyyy-MM form; unique per account. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "average_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal averageBalance;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "transaction_ref", length = 24)
    private String transactionRef;
}
