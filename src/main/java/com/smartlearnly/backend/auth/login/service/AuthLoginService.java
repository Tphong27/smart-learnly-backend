package com.smartlearnly.backend.auth.login.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.login.dto.LoginRequest;
import com.smartlearnly.backend.auth.entity.LoginHistory;
import com.smartlearnly.backend.auth.repository.LoginHistoryRepository;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthLoginService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final AuthSessionService authSessionService;
    private final LoginHistoryRepository loginHistoryRepository;

    // Xác thực email/mật khẩu, kiểm soát khóa tài khoản và phát session mới.
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthSessionService.IssuedSession login(LoginRequest request, String deviceInfo, String ipAddress) {
        String email = request.email() == null
                ? null
                : request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> {
                    recordLogin(null, email, ipAddress, deviceInfo, "email", "failed");
                    auditLogService.recordAuthentication(null, email, AuditAction.LOGIN_FAILED, AuditResult.FAILURE,
                            "Login failed", ipAddress, deviceInfo, ErrorCode.INVALID_CREDENTIALS.name());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            recordLogin(user, email, ipAddress, deviceInfo, "email", "blocked");
            auditLogService.recordAuthentication(user, email, AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                    "Login was blocked", ipAddress, deviceInfo, ErrorCode.ACCOUNT_LOCKED.name());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Account is locked until " + user.getLockedUntil());
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            ErrorCode errorCode = "pending_verify".equalsIgnoreCase(user.getStatus()) || !user.isEmailVerified()
                    ? ErrorCode.EMAIL_NOT_VERIFIED
                    : ErrorCode.ACCOUNT_INACTIVE;
            auditLogService.recordAuthentication(
                    user, email, AuditAction.LOGIN_FAILED, AuditResult.DENIED,
                    "Login was denied", ipAddress, deviceInfo, errorCode.name());
            throw new BusinessException(errorCode);
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user, now);
            boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
            recordLogin(user, email, ipAddress, deviceInfo, "email", locked ? "blocked" : "failed");
            auditLogService.recordAuthentication(
                    user,
                    email,
                    locked ? AuditAction.LOGIN_BLOCKED : AuditAction.LOGIN_FAILED,
                    locked ? AuditResult.DENIED : AuditResult.FAILURE,
                    locked ? "Login was blocked" : "Login failed",
                    ipAddress,
                    deviceInfo,
                    locked ? ErrorCode.ACCOUNT_LOCKED.name() : ErrorCode.INVALID_CREDENTIALS.name());
            throw new BusinessException(locked ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);
        recordLogin(user, email, ipAddress, deviceInfo, "email", "success");
        auditLogService.recordAuthentication(user, email, AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Login succeeded", ipAddress, deviceInfo, null);
        return authSessionService.issue(user, deviceInfo, ipAddress);
    }

    // Tăng số lần đăng nhập sai và khóa tạm tài khoản khi đạt ngưỡng cấu hình.
    private void registerFailedLogin(UserAccount user, Instant now) {
        int failures = Optional.ofNullable(user.getFailedLoginAttempts()).orElse(0) + 1;
        user.setFailedLoginAttempts(failures);
        if (failures >= authProperties.getLoginMaxFailures()) {
            user.setLockedUntil(now.plus(authProperties.getLoginLockDuration()));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }

    // Lưu lịch sử đăng nhập email để phục vụ audit và giám sát bảo mật.
    private void recordLogin(
            UserAccount user,
            String email,
            String ipAddress,
            String userAgent,
            String method,
            String status) {
        LoginHistory history = new LoginHistory();
        history.setUser(user);
        history.setEmail(email);
        history.setIpAddress(ipAddress);
        history.setUserAgent(userAgent);
        history.setLoginMethod(method);
        history.setStatus(status);
        loginHistoryRepository.save(history);
    }

}
