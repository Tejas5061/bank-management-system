package com.bankms.dto.beneficiary;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** bankName is only needed for other banks; for this bank's IFSCs it is filled in automatically. */
public record BeneficiaryRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @Size(max = 50) String nickname,
        @NotBlank @Pattern(regexp = ValidationPatterns.ACCOUNT_NUMBER, message = "must be 9-18 digits") String accountNumber,
        @NotBlank @Pattern(regexp = ValidationPatterns.IFSC, message = "must be a valid IFSC, e.g. KOSH0000001") String ifsc,
        @Size(max = 100) String bankName) {
}
