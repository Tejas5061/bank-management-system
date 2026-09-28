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

import java.time.Instant;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** True when the account is held at this bank, so the transfer can be settled in-house. */
    @Column(nullable = false)
    private boolean internal;

    /** End of the cooling period: transfers to this payee are refused before this instant. */
    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    public boolean isActive(Instant now) {
        return !activatedAt.isAfter(now);
    }
}
