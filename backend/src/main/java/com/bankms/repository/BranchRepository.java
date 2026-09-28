package com.bankms.repository;

import com.bankms.entity.Branch;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByCode(String code);

    Optional<Branch> findByIfsc(String ifsc);

    boolean existsByCode(String code);

    List<Branch> findAllByActiveTrue(Sort sort);
}
