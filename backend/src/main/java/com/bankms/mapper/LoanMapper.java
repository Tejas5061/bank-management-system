package com.bankms.mapper;

import com.bankms.dto.loan.InstallmentResponse;
import com.bankms.dto.loan.LoanResponse;
import com.bankms.entity.Loan;
import com.bankms.entity.LoanInstallment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface LoanMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountNumber", source = "account.accountNumber")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerNumber", source = "customer.customerNumber")
    @Mapping(target = "customerName", source = "customer.user.fullName")
    LoanResponse toResponse(Loan loan);

    InstallmentResponse toInstallment(LoanInstallment installment);

    List<InstallmentResponse> toInstallments(List<LoanInstallment> installments);
}
