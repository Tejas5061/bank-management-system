package com.bankms.repository;

import com.bankms.entity.LoanProduct;
import com.bankms.entity.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository extends JpaRepository<LoanProduct, LoanType> {
}
