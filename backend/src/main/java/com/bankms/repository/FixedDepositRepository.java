package com.bankms.repository;

import com.bankms.entity.FixedDeposit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {

    @EntityGraph(attributePaths = {"account", "payoutAccount"})
    @Query("select fd from FixedDeposit fd where fd.account.customer.id = :customerId")
    Page<FixedDeposit> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "payoutAccount"})
    Optional<FixedDeposit> findByAccountId(Long accountId);

    /** Taken first by the maturity job so two overlapping runs cannot pay the same FD twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fd from FixedDeposit fd where fd.id = :id")
    Optional<FixedDeposit> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select fd.id from FixedDeposit fd
            where fd.status = com.bankms.entity.FixedDepositStatus.ACTIVE and fd.maturityDate <= :today
            order by fd.maturityDate
            """)
    List<Long> findMaturedIds(@Param("today") LocalDate today);
}
