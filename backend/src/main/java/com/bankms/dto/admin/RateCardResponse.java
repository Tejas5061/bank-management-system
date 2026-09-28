package com.bankms.dto.admin;

import java.util.List;

public record RateCardResponse(List<AccountPolicyResponse> accounts, List<LoanProductResponse> loans) {
}
