package com.bankms.repository;

import com.bankms.entity.LoanInstallment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {

    /** Oldest first per loan, so an overdue EMI is always collected before a newer one. */
    @Query("""
            select i.id from LoanInstallment i
            where i.status in (com.bankms.entity.InstallmentStatus.PENDING, com.bankms.entity.InstallmentStatus.OVERDUE)
              and i.dueDate <= :today
              and i.loan.status = com.bankms.entity.LoanStatus.ACTIVE
            order by i.loan.id, i.installmentNumber
            """)
    List<Long> findDueInstallmentIds(@Param("today") LocalDate today);

    /** Taken first by the EMI job so overlapping runs cannot collect the same instalment twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LoanInstallment i join fetch i.loan where i.id = :id")
    Optional<LoanInstallment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(i) from LoanInstallment i
            where i.status = com.bankms.entity.InstallmentStatus.OVERDUE
            """)
    long countOverdue();

    @Query("""
            select count(i) > 0 from LoanInstallment i
            where i.loan.id = :loanId and i.installmentNumber < :number
              and i.status <> com.bankms.entity.InstallmentStatus.PAID
            """)
    boolean hasEarlierUnpaid(@Param("loanId") Long loanId, @Param("number") int number);
}
