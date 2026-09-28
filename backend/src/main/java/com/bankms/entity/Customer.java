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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "customer_number", nullable = false, length = 20, updatable = false)
    private String customerNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** Stored in full for KYC; it only ever leaves the API masked (see Masking). */
    @Column(name = "pan_number", nullable = false, length = 10)
    private String panNumber;

    @Column(name = "aadhaar_number", nullable = false, length = 12)
    private String aadhaarNumber;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_branch_id", nullable = false)
    private Branch homeBranch;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_remarks", length = 500)
    private String kycRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_reviewed_by")
    private User kycReviewedBy;

    @Column(name = "kyc_reviewed_at")
    private Instant kycReviewedAt;

    public boolean isKycVerified() {
        return kycStatus == KycStatus.VERIFIED;
    }

    public void reviewKyc(KycStatus status, String remarks, User reviewer, Instant now) {
        this.kycStatus = status;
        this.kycRemarks = remarks;
        this.kycReviewedBy = reviewer;
        this.kycReviewedAt = now;
    }
}
