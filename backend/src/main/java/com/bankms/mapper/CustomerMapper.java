package com.bankms.mapper;

import com.bankms.dto.customer.CustomerProfileResponse;
import com.bankms.dto.customer.CustomerSummaryResponse;
import com.bankms.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = {MaskingSupport.class, BranchMapper.class})
public interface CustomerMapper {

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "maskedPan", source = "panNumber", qualifiedByName = "maskPan")
    @Mapping(target = "maskedAadhaar", source = "aadhaarNumber", qualifiedByName = "maskAadhaar")
    CustomerProfileResponse toProfile(Customer customer);

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "homeBranchCode", source = "homeBranch.code")
    CustomerSummaryResponse toSummary(Customer customer);
}
