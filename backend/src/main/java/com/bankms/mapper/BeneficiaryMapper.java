package com.bankms.mapper;

import com.bankms.dto.beneficiary.BeneficiaryResponse;
import com.bankms.entity.Beneficiary;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(uses = MaskingSupport.class)
public interface BeneficiaryMapper {

    @Mapping(target = "maskedAccountNumber", source = "accountNumber", qualifiedByName = "maskAccount")
    @Mapping(target = "active", expression = "java(beneficiary.isActive(now))")
    BeneficiaryResponse toResponse(Beneficiary beneficiary, @Context Instant now);
}
