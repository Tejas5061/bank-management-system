package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.config.AppProperties;
import com.bankms.dto.beneficiary.BeneficiaryRequest;
import com.bankms.dto.beneficiary.BeneficiaryResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.entity.Account;
import com.bankms.entity.Beneficiary;
import com.bankms.entity.Customer;
import com.bankms.entity.NotificationType;
import com.bankms.exception.BankException;
import com.bankms.mapper.BeneficiaryMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.BeneficiaryRepository;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Saved payees. A new payee cannot receive money until the cooling period has passed: if an
 * attacker takes over a session, they cannot add their own account and drain funds at once, and the
 * real customer has been emailed in the meantime.
 */
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaries;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final BranchRepository branches;
    private final NotificationService notifications;
    private final BeneficiaryMapper mapper;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<BeneficiaryResponse> list(Long customerId, Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.of(beneficiaries.findByCustomerId(customerId, pageable), b -> mapper.toResponse(b, now));
    }

    @Audited(action = AuditAction.BENEFICIARY_ADDED, entityType = "BENEFICIARY", entityId = "#result.id",
            details = "'ifsc=' + #request.ifsc + ', account=' + T(com.bankms.util.Masking).accountNumber(#request.accountNumber)")
    @Transactional
    public BeneficiaryResponse add(Long customerId, BeneficiaryRequest request) {
        Customer customer = customers.findDetailedById(customerId)
                .orElseThrow(() -> BankException.notFound("Customer", customerId));
        String ifsc = request.ifsc().toUpperCase();
        if (beneficiaries.existsByCustomerIdAndAccountNumberAndIfsc(customerId, request.accountNumber(), ifsc)) {
            throw BankException.duplicate("This beneficiary is already saved");
        }

        boolean internal = branches.findByIfsc(ifsc).isPresent();
        String bankName;
        if (internal) {
            Account target = accounts.findByAccountNumber(request.accountNumber())
                    .filter(a -> a.getBranch().getIfsc().equals(ifsc))
                    .orElseThrow(() -> BankException.rule("No account with this number exists at branch " + ifsc));
            if (target.getCustomer().getId().equals(customerId)) {
                throw BankException.rule("This is your own account; use an own-account transfer instead");
            }
            if (target.isFixedDeposit()) {
                throw BankException.rule("Transfers cannot be made to a fixed deposit account");
            }
            bankName = properties.bank().name();
        } else {
            if (!StringUtils.hasText(request.bankName())) {
                throw BankException.rule("bankName is required for accounts at other banks");
            }
            bankName = request.bankName().trim();
        }

        Instant now = Instant.now(clock);
        Duration cooling = properties.transfer().beneficiaryCoolingPeriod();
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setCustomer(customer);
        beneficiary.setName(request.name().trim());
        beneficiary.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : null);
        beneficiary.setAccountNumber(request.accountNumber());
        beneficiary.setIfsc(ifsc);
        beneficiary.setBankName(bankName);
        beneficiary.setInternal(internal);
        beneficiary.setActivatedAt(now.plus(cooling));
        beneficiaries.save(beneficiary);

        notifications.notifyAndEmail(customer.getUser(), NotificationType.SECURITY, "New beneficiary added",
                "%s (A/c %s, %s) was added to your payees. Transfers to it open after %d minutes. If you did not do this, contact us immediately."
                        .formatted(beneficiary.getName(), Masking.accountNumber(beneficiary.getAccountNumber()), ifsc, cooling.toMinutes()));
        return mapper.toResponse(beneficiary, now);
    }

    @Audited(action = AuditAction.BENEFICIARY_DELETED, entityType = "BENEFICIARY", entityId = "#beneficiaryId")
    @Transactional
    public void delete(Long customerId, Long beneficiaryId) {
        Beneficiary beneficiary = beneficiaries.findByIdAndCustomerId(beneficiaryId, customerId)
                .orElseThrow(() -> BankException.notFound("Beneficiary", beneficiaryId));
        beneficiaries.delete(beneficiary);
    }
}
