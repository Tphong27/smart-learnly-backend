package com.smartlearnly.backend.auth.repository;

import com.smartlearnly.backend.auth.entity.OtpVerification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    long countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(String email, String purpose, Instant createdAfter);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            String email,
            String purpose
    );

    @Modifying
    @Query("""
            update OtpVerification otp
               set otp.verifiedAt = :verifiedAt
             where otp.user.id = :userId
               and otp.purpose = :purpose
               and otp.verifiedAt is null
            """)
    int markAllUnverifiedAsVerified(
            @Param("userId") UUID userId,
            @Param("purpose") String purpose,
            @Param("verifiedAt") Instant verifiedAt
    );
}
