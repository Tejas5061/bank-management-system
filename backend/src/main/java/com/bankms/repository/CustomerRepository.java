package com.bankms.repository;

import com.bankms.entity.Customer;
import com.bankms.entity.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @EntityGraph(attributePaths = {"user", "homeBranch"})
    Optional<Customer> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "homeBranch"})
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findDetailedById(@Param("id") Long id);

    /** Entity graph avoids N+1 when the list view renders each customer's user and branch. */
    @Override
    @EntityGraph(attributePaths = {"user", "homeBranch"})
    Page<Customer> findAll(Specification<Customer> spec, Pageable pageable);

    boolean existsByPanNumber(String panNumber);

    boolean existsByAadhaarNumber(String aadhaarNumber);

    long countByKycStatus(KycStatus status);
}
