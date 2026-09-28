package com.bankms.repository.projection;

import com.bankms.entity.TransactionType;

import java.math.BigDecimal;

public interface TypeTotal {

    TransactionType getType();

    long getCount();

    BigDecimal getTotal();
}
