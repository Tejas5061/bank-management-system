package com.bankms.repository.projection;

import com.bankms.entity.TransactionDirection;

import java.math.BigDecimal;

public interface MonthlyFlow {

    int getYear();

    int getMonth();

    TransactionDirection getDirection();

    BigDecimal getTotal();
}
