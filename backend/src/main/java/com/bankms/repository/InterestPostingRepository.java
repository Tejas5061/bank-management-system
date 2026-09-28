package com.bankms.repository;

import com.bankms.entity.InterestPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestPostingRepository extends JpaRepository<InterestPosting, Long> {

    boolean existsByAccountIdAndPeriod(Long accountId, String period);
}
