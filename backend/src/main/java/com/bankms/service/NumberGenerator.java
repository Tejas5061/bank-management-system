package com.bankms.service;

import com.bankms.entity.Branch;
import com.bankms.entity.NumberSequence;
import com.bankms.repository.NumberSequenceRepository;
import com.bankms.util.Luhn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-friendly identifiers from locked counter rows. Because the counter is advanced inside the
 * caller's transaction, a rolled-back registration gives its number back rather than burning it.
 */
@Service
@RequiredArgsConstructor
public class NumberGenerator {

    private final NumberSequenceRepository sequences;

    /** 12 digits: last 4 of the branch code, a 7-digit serial, and a Luhn check digit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextAccountNumber(Branch branch) {
        String body = branch.getCode().substring(branch.getCode().length() - 4) + String.format("%07d", next("ACCOUNT"));
        return body + Luhn.checkDigit(body);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCustomerNumber() {
        return "CIF" + next("CUSTOMER");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextLoanNumber() {
        return "LN" + next("LOAN");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextEmployeeCode() {
        return "EMP" + next("EMPLOYEE");
    }

    private long next(String name) {
        NumberSequence sequence = sequences.findForUpdate(name)
                .orElseThrow(() -> new IllegalStateException("Missing number sequence " + name));
        return sequence.takeNext();
    }
}
