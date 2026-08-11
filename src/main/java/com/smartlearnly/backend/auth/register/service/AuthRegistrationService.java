package com.smartlearnly.backend.auth.register.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.register.dto.RegisterRequest;
import com.smartlearnly.backend.auth.register.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.register.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.register.entity.OtpVerification;
import com.smartlearnly.backend.auth.register.repository.OtpVerificationRepository;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AuthRegistrationService.class);

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    // Tạo trainee chờ xác thực, phát OTP và ghi audit đăng ký.
    public void register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Password confirmation does not match");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("TRAINEE");
        user.setStatus("pending_verify");
        user.setFailedLoginAttempts(0);
        UserAccount savedUser = userRepository.save(user);

        issueVerificationOtp(savedUser);
        auditLogService.record(savedUser.getEmail(), "ACCOUNT_REGISTERED", "USER", savedUser.getId().toString());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    // Gửi lại OTP cho tài khoản chưa xác thực mà không tiết lộ email không tồn tại.
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueVerificationOtp);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    // Kiểm tra OTP mới nhất, tăng số lần sai và kích hoạt tài khoản khi hợp lệ.
    public void verifyEmail(VerifyEmailRequest request) {
        Instant now = Instant.now();
        String email = normalizeEmail(request.email());
        OtpVerification otp = otpVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                        email,
                        OtpVerification.EMAIL_VERIFY_PURPOSE)
                .filter(savedOtp -> savedOtp.isUsable(now))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                        "Email verification OTP is invalid or expired"));

        if (!passwordEncoder.matches(request.otpCode(), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpVerificationRepository.save(otp);
            throw new BusinessException(
                    ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                    "Email verification OTP is invalid or expired");
        }

        UserAccount user = otp.getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
            if ("pending_verify".equalsIgnoreCase(user.getStatus())) {
                user.setStatus("active");
            }
            userRepository.save(user);
        }

        otp.setVerifiedAt(now);
        otpVerificationRepository.save(otp);
        auditLogService.record(user.getEmail(), "EMAIL_VERIFIED", "USER", user.getId().toString());
    }

    // Phát OTP mới theo rate limit, vô hiệu OTP cũ và gửi email xác thực.
    private void issueVerificationOtp(UserAccount user) {
        Instant now = Instant.now();
        long recentRequests = otpVerificationRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
                user.getEmail(),
                OtpVerification.EMAIL_VERIFY_PURPOSE,
                now.minus(authProperties.getEmailVerificationRequestWindow()));
        if (recentRequests >= authProperties.getEmailVerificationRequestLimit()) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many verification OTP requests. Please try again later");
        }

        otpVerificationRepository.markAllUnverifiedAsVerified(
                user.getId(),
                OtpVerification.EMAIL_VERIFY_PURPOSE,
                now);

        String otpCode = "%06d".formatted(secureRandom.nextInt(1_000_000));;
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setOtpHash(passwordEncoder.encode(otpCode));
        otp.setPurpose(OtpVerification.EMAIL_VERIFY_PURPOSE);
        otp.setExpiresAt(now.plus(authProperties.getEmailVerificationOtpTtl()));
        otp.setAttempts(0);
        otp.setMaxAttempts(authProperties.getEmailVerificationOtpMaxAttempts());
        otpVerificationRepository.save(otp);

        logDebugToken(user.getEmail(), otpCode, otp.getExpiresAt());
        emailService.sendVerificationOtp(user.getEmail(), user.getFullName(), otpCode);
    }

    // Chỉ ghi OTP rõ trong môi trường debug được cấu hình tường minh.
    private void logDebugToken(String email, String otpCode, Instant expiresAt) {
        if (authProperties.isDebugLogTokens()) {
            log.info(
                    "Generated email-verification-otp token for email={} token={} expiresAt={}",
                    email,
                    otpCode,
                    expiresAt);
        }
    }

    // Chuẩn hóa email để mọi truy vấn auth không phụ thuộc chữ hoa/thường.
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
