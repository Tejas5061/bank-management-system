package com.bankms.repository;

import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.repository.projection.DailyVolume;
import com.bankms.repository.projection.MonthlyFlow;
import com.bankms.repository.projection.TypeTotal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** Called with the account row already locked, so the running total cannot change underneath us. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.id = :accountId and t.type = :type
              and t.status = com.bankms.entity.TransactionStatus.SUCCESS and t.valueDate = :date
            """)
    BigDecimal sumSuccessfulByTypeOnDate(@Param("accountId") Long accountId,
                                         @Param("type") TransactionType type,
                                         @Param("date") LocalDate date);

    /** Latest successful entry strictly before a date: its balanceAfter is the opening balance. */
    Optional<Transaction> findTopByAccountIdAndStatusAndValueDateBeforeOrderByIdDesc(
            Long accountId, TransactionStatus status, LocalDate date);

    /** Id order equals balance-change order per account, because inserts happen under the row lock. */
    List<Transaction> findByAccountIdAndStatusAndValueDateBetweenOrderByIdAsc(
            Long accountId, TransactionStatus status, LocalDate from, LocalDate to);

    @Query("""
            select t from Transaction t join fetch t.account a
            where a.customer.id = :customerId
            order by t.id desc
            """)
    List<Transaction> findRecentForCustomer(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Money in/out per month for a customer, ignoring moves between the customer's own accounts
     * (e.g. savings to FD), which would otherwise count as both income and spending.
     */
    @Query("""
            select year(t.valueDate) as year, month(t.valueDate) as month, t.direction as direction,
                   sum(t.amount) as total
            from Transaction t
            where t.account.customer.id = :customerId
              and t.status = com.bankms.entity.TransactionStatus.SUCCESS
              and t.valueDate >= :from
              and (t.counterpartyAccount is null or t.counterpartyAccount not in
                   (select own.accountNumber from Account own where own.customer.id = :customerId))
            group by year(t.valueDate), month(t.valueDate), t.direction
            """)
    List<MonthlyFlow> monthlyFlowForCustomer(@Param("customerId") Long customerId, @Param("from") LocalDate from);

    @Query("""
            select t.type as type, count(t) as count, sum(t.amount) as total
            from Transaction t
            where t.account.customer.id = :customerId
              and t.status = com.bankms.entity.TransactionStatus.SUCCESS
              and t.direction = com.bankms.entity.TransactionDirection.DEBIT
              and t.valueDate >= :from
              and (t.counterpartyAccount is null or t.counterpartyAccount not in
                   (select own.accountNumber from Account own where own.customer.id = :customerId))
            group by t.type
            """)
    List<TypeTotal> spendingByTypeForCustomer(@Param("customerId") Long customerId, @Param("from") LocalDate from);

    @Query("""
            select t.valueDate as valueDate, t.direction as direction, count(t) as count, sum(t.amount) as total
            from Transaction t
            where t.status = com.bankms.entity.TransactionStatus.SUCCESS and t.valueDate >= :from
            group by t.valueDate, t.direction
            """)
    List<DailyVolume> dailyVolumeSince(@Param("from") LocalDate from);

    @Query("""
            select t.type as type, count(t) as count, coalesce(sum(t.amount), 0) as total
            from Transaction t
            where t.status = com.bankms.entity.TransactionStatus.SUCCESS and t.valueDate = :date
            group by t.type
            """)
    List<TypeTotal> totalsByTypeOn(@Param("date") LocalDate date);
}
