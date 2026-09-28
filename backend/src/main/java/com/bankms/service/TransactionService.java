package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.deposit.FixedDepositRequest;
import com.bankms.dto.deposit.FixedDepositResponse;
import com.bankms.dto.transaction.BeneficiaryTransferRequest;
import com.bankms.dto.transaction.CashRequest;
import com.bankms.dto.transaction.CashResponse;
import com.bankms.dto.transaction.InternalTransferRequest;
import com.bankms.dto.transaction.TransferResponse;
import com.bankms.entity.TransactionChannel;
import com.bankms.exception.TransactionDeclinedException;
import com.bankms.security.AuthUser;
import com.bankms.service.idempotency.IdempotencyContext;
import com.bankms.service.idempotency.IdempotencyService;
import com.bankms.service.idempotency.Idempotent;
import com.bankms.service.ledger.DeclinedTransactionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Entry point for every money-moving request. Deliberately NOT transactional: it wraps the
 * transactional operation (TransferService / FixedDepositService) with
 * <ul>
 *   <li>idempotency (replay or execute),</li>
 *   <li>recording a FAILED ledger row when a banking rule declines the request, which has to happen
 *       after the declining transaction has rolled back, and</li>
 *   <li>the audit trail (@Audited, also outside the transaction).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String REPLAY_NOTE = "(#result?.replayed == true ? ' (idempotent replay)' : '')";

    private final TransferService transfers;
    private final FixedDepositService fixedDeposits;
    private final CustomerService customers;
    private final IdempotencyService idempotency;
    private final DeclinedTransactionRecorder declines;

    @Audited(action = AuditAction.TRANSFER, entityType = "ACCOUNT", entityId = "#request.fromAccountId",
            details = "'own account transfer to account ' + #request.toAccountId + ', amount=' + #request.amount + " + REPLAY_NOTE)
    public Idempotent<TransferResponse> internalTransfer(AuthUser user, String idempotencyKey, InternalTransferRequest request) {
        IdempotencyContext key = idempotency.context(user.id(), idempotencyKey, "POST /api/v1/transfers/internal", request);
        return recordingDeclines(TransactionChannel.ONLINE, "Own account transfer (declined)", user.id(),
                () -> idempotency.execute(key, TransferResponse.class,
                        () -> transfers.internalTransfer(key, user.id(), request)));
    }

    @Audited(action = AuditAction.TRANSFER, entityType = "ACCOUNT", entityId = "#request.fromAccountId",
            details = "'beneficiary ' + #request.beneficiaryId + ', amount=' + #request.amount + " + REPLAY_NOTE)
    public Idempotent<TransferResponse> beneficiaryTransfer(AuthUser user, String idempotencyKey,
                                                            BeneficiaryTransferRequest request) {
        Long customerId = customers.customerIdForUser(user.id());
        IdempotencyContext key = idempotency.context(user.id(), idempotencyKey, "POST /api/v1/transfers/beneficiary", request);
        return recordingDeclines(TransactionChannel.ONLINE, "Beneficiary transfer (declined)", user.id(),
                () -> idempotency.execute(key, TransferResponse.class,
                        () -> transfers.beneficiaryTransfer(key, user.id(), customerId, request)));
    }

    @Audited(action = AuditAction.CASH_DEPOSIT, entityType = "ACCOUNT", entityId = "#request.accountNumber",
            details = "'amount=' + #request.amount + " + REPLAY_NOTE)
    public Idempotent<CashResponse> cashDeposit(AuthUser teller, String idempotencyKey, CashRequest request) {
        IdempotencyContext key = idempotency.context(teller.id(), idempotencyKey, "POST /api/v1/staff/cash/deposits", request);
        return recordingDeclines(TransactionChannel.BRANCH, "Cash deposit (declined)", teller.id(),
                () -> idempotency.execute(key, CashResponse.class,
                        () -> transfers.cashDeposit(key, teller.id(), request)));
    }

    @Audited(action = AuditAction.CASH_WITHDRAWAL, entityType = "ACCOUNT", entityId = "#request.accountNumber",
            details = "'amount=' + #request.amount + " + REPLAY_NOTE)
    public Idempotent<CashResponse> cashWithdrawal(AuthUser teller, String idempotencyKey, CashRequest request) {
        IdempotencyContext key = idempotency.context(teller.id(), idempotencyKey, "POST /api/v1/staff/cash/withdrawals", request);
        return recordingDeclines(TransactionChannel.BRANCH, "Cash withdrawal (declined)", teller.id(),
                () -> idempotency.execute(key, CashResponse.class,
                        () -> transfers.cashWithdrawal(key, teller.id(), request)));
    }

    @Audited(action = AuditAction.FD_OPENED, entityType = "ACCOUNT", entityId = "#request.sourceAccountId",
            details = "'principal=' + #request.principal + ', tenure=' + #request.tenureMonths + 'm' + " + REPLAY_NOTE)
    public Idempotent<FixedDepositResponse> openFixedDeposit(AuthUser user, String idempotencyKey, FixedDepositRequest request) {
        IdempotencyContext key = idempotency.context(user.id(), idempotencyKey, "POST /api/v1/me/fixed-deposits", request);
        return recordingDeclines(TransactionChannel.ONLINE, "Fixed deposit booking (declined)", user.id(),
                () -> idempotency.execute(key, FixedDepositResponse.class,
                        () -> fixedDeposits.open(key, user.id(), request)));
    }

    private <T> T recordingDeclines(TransactionChannel channel, String description, Long userId, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (TransactionDeclinedException declined) {
            try {
                declines.record(declined, channel, description, userId);
            } catch (RuntimeException e) {
                log.warn("Could not record declined transaction for account {}", declined.getAccountId(), e);
            }
            throw declined;
        }
    }
}
