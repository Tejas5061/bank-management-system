package com.bankms.repository;

import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountPolicyRepository extends JpaRepository<AccountPolicy, AccountType> {
}
