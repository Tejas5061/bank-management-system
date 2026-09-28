package com.bankms.repository;

import com.bankms.entity.PasswordResetOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /** Locked so parallel guesses cannot race past the attempt counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOtp> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);

    /** A new OTP request supersedes any earlier one. */
    @Modifying
    @Query("update PasswordResetOtp o set o.consumedAt = :now where o.user.id = :userId and o.consumedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("now") Instant now);
}
