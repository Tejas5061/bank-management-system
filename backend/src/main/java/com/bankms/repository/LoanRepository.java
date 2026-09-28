package com.bankms.repository;

import com.bankms.entity.Loan;
import com.bankms.entity.LoanStatus;
import com.bankms.repository.projection.LoanPortfolioRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @EntityGraph(attributePaths = {"account", "customer", "customer.user"})
    Page<Loan> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "customer", "customer.user"})
    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"account", "customer", "customer.user"})
    Page<Loan> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"account", "customer", "customer.user", "installments"})
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findDetailedById(@Param("id") Long id);

    @Query("""
            select l.loanType as loanType, l.status as status, count(l) as loans,
                   coalesce(sum(l.principal), 0) as principal,
                   coalesce(sum(l.outstandingPrincipal), 0) as outstanding
            from Loan l
            group by l.loanType, l.status
            """)
    List<LoanPortfolioRow> portfolio();

    long countByStatus(LoanStatus status);
}
