package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.admin.AccountPolicyRequest;
import com.bankms.dto.admin.AccountPolicyResponse;
import com.bankms.dto.admin.LoanProductRequest;
import com.bankms.dto.admin.LoanProductResponse;
import com.bankms.dto.admin.RateCardResponse;
import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AccountType;
import com.bankms.entity.LoanProduct;
import com.bankms.entity.LoanType;
import com.bankms.exception.BankException;
import com.bankms.mapper.AdminMapper;
import com.bankms.repository.AccountPolicyRepository;
import com.bankms.repository.LoanProductRepository;
import com.bankms.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Account policies (interest, minimum balance, daily limit) and loan products, managed by admins. */
@Service
@RequiredArgsConstructor
public class RateService {

    private final AccountPolicyRepository policies;
    private final LoanProductRepository loanProducts;
    private final AdminMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountPolicy policy(AccountType type) {
        return policies.findById(type).orElseThrow(() -> new IllegalStateException("No policy for " + type));
    }

    @Transactional(readOnly = true)
    public LoanProduct loanProduct(LoanType type) {
        return loanProducts.findById(type).orElseThrow(() -> BankException.notFound("Loan product", type));
    }

    @Transactional(readOnly = true)
    public RateCardResponse rateCard() {
        return new RateCardResponse(
                policies.findAll(Sort.by("accountType")).stream().map(mapper::toPolicy).toList(),
                loanProducts.findAll(Sort.by("loanType")).stream().map(mapper::toLoanProduct).toList());
    }

    @Audited(action = AuditAction.RATE_CHANGED, entityType = "ACCOUNT_POLICY", entityId = "#type",
            details = "'rate=' + #request.interestRate + ', minBalance=' + #request.minimumBalance + ', dailyLimit=' + #request.dailyTransferLimit")
    @Transactional
    public AccountPolicyResponse updatePolicy(AccountType type, AccountPolicyRequest request, Long adminUserId) {
        AccountPolicy policy = policy(type);
        policy.setInterestRate(request.interestRate());
        policy.setMinimumBalance(Money.normalize(request.minimumBalance()));
        policy.setDailyTransferLimit(Money.normalize(request.dailyTransferLimit()));
        policy.setUpdatedBy(adminUserId);
        policy.setUpdatedAt(Instant.now(clock));
        return mapper.toPolicy(policy);
    }

    @Audited(action = AuditAction.RATE_CHANGED, entityType = "LOAN_PRODUCT", entityId = "#type",
            details = "'rate=' + #request.interestRate + ', amount=' + #request.minAmount + '-' + #request.maxAmount")
    @Transactional
    public LoanProductResponse updateLoanProduct(LoanType type, LoanProductRequest request, Long adminUserId) {
        LoanProduct product = loanProduct(type);
        product.setInterestRate(request.interestRate());
        product.setMinAmount(Money.normalize(request.minAmount()));
        product.setMaxAmount(Money.normalize(request.maxAmount()));
        product.setMinTenureMonths(request.minTenureMonths());
        product.setMaxTenureMonths(request.maxTenureMonths());
        product.setUpdatedBy(adminUserId);
        product.setUpdatedAt(Instant.now(clock));
        return mapper.toLoanProduct(product);
    }
}
