package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.admin.JobRunResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.loan.EmiCalculationRequest;
import com.bankms.dto.loan.EmiCalculationResponse;
import com.bankms.dto.loan.LoanApplicationRequest;
import com.bankms.dto.loan.LoanDecisionRequest;
import com.bankms.dto.loan.LoanDetailResponse;
import com.bankms.dto.loan.LoanResponse;
import com.bankms.entity.Account;
import com.bankms.entity.Customer;
import com.bankms.entity.InstallmentStatus;
import com.bankms.entity.Loan;
import com.bankms.entity.LoanInstallment;
import com.bankms.entity.LoanProduct;
import com.bankms.entity.LoanStatus;
import com.bankms.entity.NotificationType;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.mapper.LoanMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.LoanInstallmentRepository;
import com.bankms.repository.LoanRepository;
import com.bankms.repository.UserRepository;
import com.bankms.service.calc.EmiCalculator;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.ledger.PostingDetails;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import com.bankms.util.Money;
import com.bankms.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final RateService rates;
    private final EmiCalculator emiCalculator;
    private final LedgerService ledger;
    private final NumberGenerator numbers;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final LoanMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EmiCalculationResponse calculate(EmiCalculationRequest request) {
        List<EmiCalculator.Installment> schedule =
                emiCalculator.schedule(request.principal(), request.annualRate(), request.tenureMonths(), null);
        BigDecimal totalPayment = schedule.stream().map(EmiCalculator.Installment::emi).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EmiCalculationResponse(
                request.principal(),
                request.annualRate(),
                request.tenureMonths(),
                emiCalculator.emi(request.principal(), request.annualRate(), request.tenureMonths()),
                totalPayment.subtract(request.principal()),
                totalPayment,
                schedule.stream()
                        .map(i -> new EmiCalculationResponse.ScheduleRow(i.number(), i.emi(), i.principal(), i.interest(), i.closingPrincipal()))
                        .toList());
    }

    @Audited(action = AuditAction.LOAN_APPLIED, entityType = "LOAN", entityId = "#result.loanNumber",
            details = "#request.loanType + ' ' + #request.amount + ' for ' + #request.tenureMonths + 'm'")
    @Transactional
    public LoanResponse apply(Long customerId, LoanApplicationRequest request) {
        Customer customer = customers.findDetailedById(customerId)
                .orElseThrow(() -> BankException.notFound("Customer", customerId));
        if (!customer.isKycVerified()) {
            throw new BankException(ErrorCode.KYC_NOT_VERIFIED, "Loans can be applied for once your KYC is verified");
        }
        Account account = accounts.findByIdAndCustomerId(request.accountId(), customerId)
                .orElseThrow(() -> BankException.notFound("Account", request.accountId()));
        if (account.isFixedDeposit() || !account.isActive()) {
            throw BankException.rule("Choose an active savings or current account for disbursement and EMIs");
        }
        LoanProduct product = rates.loanProduct(request.loanType());
        BigDecimal amount = Money.normalize(request.amount());
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw BankException.rule("A %s loan must be between %s and %s".formatted(request.loanType().name().toLowerCase(),
                    Money.format(product.getMinAmount()), Money.format(product.getMaxAmount())));
        }
        if (request.tenureMonths() < product.getMinTenureMonths() || request.tenureMonths() > product.getMaxTenureMonths()) {
            throw BankException.rule("Tenure for a %s loan must be %d-%d months".formatted(request.loanType().name().toLowerCase(),
                    product.getMinTenureMonths(), product.getMaxTenureMonths()));
        }

        Loan loan = new Loan();
        loan.setLoanNumber(numbers.nextLoanNumber());
        loan.setCustomer(customer);
        loan.setAccount(account);
        loan.setLoanType(request.loanType());
        loan.setPrincipal(amount);
        loan.setInterestRate(product.getInterestRate());
        loan.setTenureMonths(request.tenureMonths());
        loan.setEmiAmount(emiCalculator.emi(amount, product.getInterestRate(), request.tenureMonths()));
        loan.setOutstandingPrincipal(amount);
        loan.setPurpose(request.purpose().trim());
        loan.setStatus(LoanStatus.PENDING);
        loan.setAppliedAt(Instant.now(clock));
        loans.save(loan);

        notifications.notify(customer.getUser(), NotificationType.LOAN, "Loan application received",
                "Your %s loan application %s for %s is under review. Estimated EMI: %s for %d months."
                        .formatted(request.loanType().name().toLowerCase(), loan.getLoanNumber(), Money.format(amount),
                                Money.format(loan.getEmiAmount()), loan.getTenureMonths()));
        return mapper.toResponse(loan);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> listForCustomer(Long customerId, Pageable pageable) {
        return PageResponse.of(loans.findByCustomerId(customerId, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> list(LoanStatus status, Pageable pageable) {
        Page<Loan> page = status == null ? loans.findAll(pageable) : loans.findByStatus(status, pageable);
        return PageResponse.of(page, mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public LoanDetailResponse detailForCustomer(Long customerId, Long loanId) {
        Loan loan = loans.findDetailedById(loanId)
                .filter(l -> l.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> BankException.notFound("Loan", loanId));
        return toDetail(loan);
    }

    @Transactional(readOnly = true)
    public LoanDetailResponse detail(Long loanId) {
        return loans.findDetailedById(loanId).map(this::toDetail)
                .orElseThrow(() -> BankException.notFound("Loan", loanId));
    }

    /**
     * Approves and disburses in one transaction. The loan's @Version column makes a concurrent
     * second approval fail at commit (409) instead of disbursing twice.
     */
    @Audited(action = AuditAction.LOAN_APPROVED, entityType = "LOAN", entityId = "#loanId", details = "#request.remarks")
    @Transactional
    public LoanDetailResponse approve(Long loanId, Long reviewerUserId, LoanDecisionRequest request) {
        Loan loan = pendingLoan(loanId);
        if (!loan.getCustomer().isKycVerified()) {
            throw new BankException(ErrorCode.KYC_NOT_VERIFIED, "The customer's KYC is no longer verified");
        }
        Account account = ledger.lock(loan.getAccount().getId());
        if (!account.isActive()) {
            throw BankException.rule("The disbursement account is " + account.getStatus().name().toLowerCase());
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        loan.review(LoanStatus.ACTIVE, users.getReferenceById(reviewerUserId), request.remarks().trim(), now);
        loan.setDisbursedAt(now);

        String reference = references.next();
        ledger.post(account, TransactionDirection.CREDIT, loan.getPrincipal(), TransactionType.LOAN_DISBURSEMENT, reference,
                PostingDetails.system("Loan %s disbursed".formatted(loan.getLoanNumber())));

        List<EmiCalculator.Installment> schedule =
                emiCalculator.schedule(loan.getPrincipal(), loan.getInterestRate(), loan.getTenureMonths(), today);
        loan.setEmiAmount(schedule.getFirst().emi());
        for (EmiCalculator.Installment row : schedule) {
            LoanInstallment installment = new LoanInstallment();
            installment.setInstallmentNumber(row.number());
            installment.setDueDate(row.dueDate());
            installment.setEmiAmount(row.emi());
            installment.setPrincipalComponent(row.principal());
            installment.setInterestComponent(row.interest());
            installment.setClosingPrincipal(row.closingPrincipal());
            loan.addInstallment(installment);
        }

        notifications.notifyAndEmail(loan.getCustomer().getUser(), NotificationType.LOAN, "Loan approved and disbursed",
                "Your loan %s of %s has been credited to A/c %s. EMI of %s will be auto-debited monthly from %s."
                        .formatted(loan.getLoanNumber(), Money.format(loan.getPrincipal()),
                                Masking.accountNumber(account.getAccountNumber()), Money.format(loan.getEmiAmount()),
                                schedule.getFirst().dueDate()));
        return toDetail(loan);
    }

    @Audited(action = AuditAction.LOAN_REJECTED, entityType = "LOAN", entityId = "#loanId", details = "#request.remarks")
    @Transactional
    public LoanDetailResponse reject(Long loanId, Long reviewerUserId, LoanDecisionRequest request) {
        Loan loan = pendingLoan(loanId);
        loan.review(LoanStatus.REJECTED, users.getReferenceById(reviewerUserId), request.remarks().trim(), Instant.now(clock));
        notifications.notifyAndEmail(loan.getCustomer().getUser(), NotificationType.LOAN, "Loan application declined",
                "Your loan application %s was not approved. Remarks: %s".formatted(loan.getLoanNumber(), request.remarks().trim()));
        return toDetail(loan);
    }

    /**
     * Collects every EMI due today or earlier, oldest first. Each instalment is its own
     * transaction; if one fails for lack of funds, later instalments of the same loan wait.
     */
    public JobRunResponse collectDueInstallments() {
        List<Long> due = installments.findDueInstallmentIds(LocalDate.now(clock));
        Set<Long> loansBehind = new HashSet<>();
        int paid = 0;
        int missed = 0;
        int failed = 0;
        BigDecimal total = Money.ZERO;
        for (Long id : due) {
            try {
                EmiOutcome outcome = transactionTemplate.execute(status -> collect(id, loansBehind));
                if (outcome != null && outcome.paid()) {
                    paid++;
                    total = total.add(outcome.amount());
                } else {
                    missed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("EMI collection failed for instalment {}", id, e);
            }
        }
        log.info("EMI run: {} due, {} collected, {} missed, {} failed, {} total", due.size(), paid, missed, failed, total);
        return new JobRunResponse("emi-debit", due.size(), paid, missed, failed, total);
    }

    private EmiOutcome collect(Long installmentId, Set<Long> loansBehind) {
        LoanInstallment installment = installments.findByIdForUpdate(installmentId).orElse(null);
        if (installment == null || installment.getStatus() == InstallmentStatus.PAID) {
            return EmiOutcome.skipped();
        }
        Loan loan = installment.getLoan();
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            return EmiOutcome.skipped();
        }
        boolean firstMiss = installment.getStatus() == InstallmentStatus.PENDING;
        if (loansBehind.contains(loan.getId())
                || installments.hasEarlierUnpaid(loan.getId(), installment.getInstallmentNumber())) {
            installment.markOverdue("An earlier EMI is still unpaid");
            return EmiOutcome.skipped();
        }

        Account account = ledger.lock(loan.getAccount().getId());
        BigDecimal emi = installment.getEmiAmount();
        String problem = !account.isActive() ? "Account is " + account.getStatus().name().toLowerCase()
                : account.getBalance().compareTo(emi) < 0 ? "Insufficient balance" : null;
        if (problem != null) {
            installment.markOverdue(problem);
            loansBehind.add(loan.getId());
            if (firstMiss) {
                notifications.notifyAndEmail(loan.getCustomer().getUser(), NotificationType.LOAN, "EMI payment failed",
                        "We could not collect EMI %d of %s for loan %s (%s). Please add funds to A/c %s; we will retry daily."
                                .formatted(installment.getInstallmentNumber(), Money.format(emi), loan.getLoanNumber(),
                                        problem.toLowerCase(), Masking.accountNumber(account.getAccountNumber())));
            }
            return EmiOutcome.skipped();
        }

        Instant now = Instant.now(clock);
        String reference = references.next();
        // EMIs may take the balance below the minimum; the bank collects what it is owed first.
        ledger.post(account, TransactionDirection.DEBIT, emi, TransactionType.EMI_DEBIT, reference,
                PostingDetails.system("EMI %d/%d for loan %s".formatted(installment.getInstallmentNumber(),
                        loan.getTenureMonths(), loan.getLoanNumber())));
        installment.markPaid(reference, now);
        loan.setOutstandingPrincipal(installment.getClosingPrincipal());
        boolean closed = installment.getInstallmentNumber() == loan.getTenureMonths();
        if (closed) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedAt(now);
        }
        notifications.notify(loan.getCustomer().getUser(), NotificationType.LOAN,
                closed ? "Loan closed" : "EMI paid",
                closed ? "Your final EMI was collected and loan %s is now closed. Thank you!".formatted(loan.getLoanNumber())
                        : "EMI %d of %s for loan %s was debited from A/c %s. Ref %s".formatted(installment.getInstallmentNumber(),
                        Money.format(emi), loan.getLoanNumber(), Masking.accountNumber(account.getAccountNumber()), reference));
        return new EmiOutcome(true, emi);
    }

    private Loan pendingLoan(Long loanId) {
        Loan loan = loans.findDetailedById(loanId).orElseThrow(() -> BankException.notFound("Loan", loanId));
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw BankException.rule("This application has already been " + loan.getStatus().name().toLowerCase());
        }
        return loan;
    }

    private LoanDetailResponse toDetail(Loan loan) {
        int paid = (int) loan.getInstallments().stream().filter(i -> i.getStatus() == InstallmentStatus.PAID).count();
        return new LoanDetailResponse(mapper.toResponse(loan), mapper.toInstallments(loan.getInstallments()), paid);
    }

    private record EmiOutcome(boolean paid, BigDecimal amount) {
        static EmiOutcome skipped() {
            return new EmiOutcome(false, BigDecimal.ZERO);
        }
    }
}
