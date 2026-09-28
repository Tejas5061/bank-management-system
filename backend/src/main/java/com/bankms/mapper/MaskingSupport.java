package com.bankms.mapper;

import com.bankms.util.Masking;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/** Qualified mapping methods MapStruct mappers reference via {@code qualifiedByName}. */
@Component
public class MaskingSupport {

    @Named("maskPan")
    public String maskPan(String pan) {
        return Masking.pan(pan);
    }

    @Named("maskAadhaar")
    public String maskAadhaar(String aadhaar) {
        return Masking.aadhaar(aadhaar);
    }

    @Named("maskAccount")
    public String maskAccount(String accountNumber) {
        return Masking.accountNumber(accountNumber);
    }
}
