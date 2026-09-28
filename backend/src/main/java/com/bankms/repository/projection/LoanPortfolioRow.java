package com.bankms.repository.projection;

import com.bankms.entity.LoanStatus;
import com.bankms.entity.LoanType;

import java.math.BigDecimal;

public interface LoanPortfolioRow {

    LoanType getLoanType();

    LoanStatus getStatus();

    long getLoans();

    BigDecimal getPrincipal();

    BigDecimal getOutstanding();
}
