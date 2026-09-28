package com.bankms.repository.projection;

import com.bankms.entity.TransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyVolume {

    LocalDate getValueDate();

    TransactionDirection getDirection();

    long getCount();

    BigDecimal getTotal();
}
