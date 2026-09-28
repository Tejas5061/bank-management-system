package com.bankms.repository;

import com.bankms.entity.Beneficiary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    Page<Beneficiary> findByCustomerId(Long customerId, Pageable pageable);

    Optional<Beneficiary> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByCustomerIdAndAccountNumberAndIfsc(Long customerId, String accountNumber, String ifsc);
}
