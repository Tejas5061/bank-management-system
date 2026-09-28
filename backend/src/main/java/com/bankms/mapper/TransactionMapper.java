package com.bankms.mapper;

import com.bankms.dto.transaction.TransactionResponse;
import com.bankms.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(uses = MaskingSupport.class)
public interface TransactionMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountNumber", source = "account.accountNumber")
    @Mapping(target = "counterpartyAccount", source = "counterpartyAccount", qualifiedByName = "maskAccount")
    TransactionResponse toResponse(Transaction transaction);

    List<TransactionResponse> toResponses(List<Transaction> transactions);
}
