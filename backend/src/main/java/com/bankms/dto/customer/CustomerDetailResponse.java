package com.bankms.dto.customer;

import com.bankms.dto.account.AccountResponse;

import java.util.List;

public record CustomerDetailResponse(CustomerProfileResponse profile, List<AccountResponse> accounts) {
}
