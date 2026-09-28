package com.bankms.dto.customer;

import com.bankms.dto.branch.BranchSummary;
import com.bankms.entity.KycStatus;

import java.time.Instant;
import java.time.LocalDate;

/** PAN and Aadhaar are always masked, for staff as much as for the customer. */
public record CustomerProfileResponse(
        Long id,
        String customerNumber,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String maskedPan,
        String maskedAadhaar,
        String addressLine,
        String city,
        String state,
        String pincode,
        BranchSummary homeBranch,
        KycStatus kycStatus,
        String kycRemarks,
        Instant kycReviewedAt,
        Instant createdAt) {
}
