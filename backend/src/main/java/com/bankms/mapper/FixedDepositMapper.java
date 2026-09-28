package com.bankms.mapper;

import com.bankms.dto.deposit.FixedDepositResponse;
import com.bankms.entity.FixedDeposit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface FixedDepositMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountNumber", source = "account.accountNumber")
    @Mapping(target = "payoutAccountNumber", source = "payoutAccount.accountNumber")
    @Mapping(target = "interestEarned", expression = "java(fixedDeposit.getMaturityAmount().subtract(fixedDeposit.getPrincipal()))")
    FixedDepositResponse toResponse(FixedDeposit fixedDeposit);
}
