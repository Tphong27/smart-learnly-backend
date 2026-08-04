package com.smartlearnly.backend.auth.registration.repository;

import com.smartlearnly.backend.auth.registration.entity.OtpVerification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    // Đếm số yêu cầu OTP gần đây để service áp dụng giới hạn gửi lại.
    long countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(String email, String purpose, Instant createdAfter);

    // Lấy OTP chưa xác thực mới nhất của email và mục đích tương ứng.
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            String email,
            String purpose
    );

    @Modifying
    // Đánh dấu toàn bộ OTP cũ đã xử lý trước khi phát OTP mới.
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
