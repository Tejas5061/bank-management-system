package com.bankms.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyVolumePoint(LocalDate date, BigDecimal credits, BigDecimal debits, long transactions) {
}
