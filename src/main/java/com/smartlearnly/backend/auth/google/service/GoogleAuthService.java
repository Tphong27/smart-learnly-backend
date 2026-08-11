package com.smartlearnly.backend.auth.google.service;

import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {
    private final GoogleIdTokenService googleIdTokenService;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogService auditLogService;

    // Xác thực Google ID token, liên kết hoặc tạo tài khoản rồi phát session mới.
    @Transactional
    public AuthSessionService.IssuedSession login(
            GoogleLoginRequest request,
            String deviceInfo,
            String ipAddress) {
        GoogleIdTokenService.GoogleIdentity identity = googleIdTokenService.verify(request.idToken());
        String email = identity.email() == null
                ? null
                : identity.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByGoogleIdAndDeletedAtIsNull(identity.subject())
                .orElseGet(() -> linkOrCreateGoogleUser(identity, email));

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        recordLogin(user, email, ipAddress, deviceInfo);
        auditLogService.recordAuthentication(user, email, AuditAction.GOOGLE_LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Google login succeeded", ipAddress, deviceInfo, null);
        return authSessionService.issue(user, deviceInfo, ipAddress);
    }

    // Liên kết Google vào tài khoản cùng email hoặc tạo trainee mới đã xác thực.
    private UserAccount linkOrCreateGoogleUser(GoogleIdTokenService.GoogleIdentity identity, String email) {
        Optional<UserAccount> existingUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (existingUser.isPresent()) {
            UserAccount user = existingUser.get();
            user.setGoogleId(identity.subject());
            if (!user.isEmailVerified()) {
                user.setEmailVerifiedAt(Instant.now());
                user.setStatus("active");
            }
            if (user.getAvatarUrl() == null && identity.avatarUrl() != null) {
                user.setAvatarUrl(identity.avatarUrl());
            }
            return userRepository.save(user);
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setGoogleId(identity.subject());
        user.setFullName(identity.fullName() == null || identity.fullName().isBlank() ? email : identity.fullName());
        user.setAvatarUrl(identity.avatarUrl());
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        return userRepository.save(user);
    }

    // Lưu lịch sử đăng nhập Google để phục vụ audit và giám sát bảo mật.
    private void recordLogin(UserAccount user, String email, String ipAddress, String userAgent) {
        LoginHistory history = new LoginHistory();
        history.setUser(user);
        history.setEmail(email);
        history.setIpAddress(ipAddress);
        history.setUserAgent(userAgent);
        history.setLoginMethod("google");
        history.setStatus("success");
        loginHistoryRepository.save(history);
    }

}
