package com.bankms.dto.admin;

import java.math.BigDecimal;

public record JobRunResponse(String job, int processed, int succeeded, int skipped, int failed, BigDecimal totalAmount) {
}
