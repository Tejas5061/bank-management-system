package com.bankms.repository;

import com.bankms.entity.Role;
import com.bankms.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are normalised to lower case before they are stored or looked up. */
    Optional<User> findByEmail(String email);

    /**
     * Login locks the user row so that concurrent wrong-password attempts cannot race on the
     * failed-attempt counter (lost update) and slip past the lockout threshold.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
