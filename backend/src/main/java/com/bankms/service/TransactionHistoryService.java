package com.bankms.service;

import com.bankms.dto.common.PageResponse;
import com.bankms.dto.transaction.TransactionResponse;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.mapper.TransactionMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionMapper mapper;

    /** Dynamic filters through a JPA Specification; only the predicates actually supplied are added. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long accountId, LocalDate from, LocalDate to,
                                                     TransactionType type, TransactionStatus status, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw BankException.notFound("Account", accountId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw BankException.rule("'from' must be on or before 'to'");
        }
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("account").get("id"), accountId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("valueDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("valueDate"), to));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(transactions.findAll(spec, pageable), mapper::toResponse);
    }
}
