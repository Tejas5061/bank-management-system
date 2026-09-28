package com.bankms.repository;

import com.bankms.entity.Account;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.repository.projection.DepositSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = {"customer", "customer.user", "branch"})
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findWithOwnerByAccountNumber(@Param("accountNumber") String accountNumber);

    @EntityGraph(attributePaths = {"customer", "customer.user", "branch"})
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findWithOwnerById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"branch"})
    List<Account> findByCustomerIdOrderByOpenedAtAsc(Long customerId);

    @EntityGraph(attributePaths = {"branch", "customer"})
    Optional<Account> findByIdAndCustomerId(Long id, Long customerId);

    @Query("select count(a) > 0 from Account a where a.id = :accountId and a.customer.user.id = :userId")
    boolean isOwnedByUser(@Param("accountId") Long accountId, @Param("userId") Long userId);

    @Query("select a.id from Account a where a.accountType = :type and a.status <> com.bankms.entity.AccountStatus.CLOSED order by a.id")
    List<Long> findOpenAccountIdsByType(@Param("type") AccountType type);

    @Query("""
            select a.accountType as accountType, count(a) as accounts, coalesce(sum(a.balance), 0) as balance
            from Account a
            where a.status <> com.bankms.entity.AccountStatus.CLOSED
            group by a.accountType
            """)
    List<DepositSummary> summarizeDeposits();

    long countByStatus(AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);
}
