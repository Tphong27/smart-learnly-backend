package com.smartlearnly.backend.auth.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.session.entity.RefreshToken;
import com.smartlearnly.backend.auth.session.repository.RefreshTokenRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuditLogService auditLogService;

    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        authSessionService = new AuthSessionService(
                refreshTokenRepository, jwtTokenService, properties, auditLogService);
    }

    /**
     * Kịch bản: phát một session mới cho user active.
     * Given: JWT service trả access token đã ký.
     * When: issue tạo refresh token.
     * Then: client nhận token rõ ngẫu nhiên, database chỉ nhận SHA-256 hash, TTL/metadata/user đúng
     * và response chứa access-token profile với expiresIn 15 phút.
     * Ý nghĩa bảo mật: refresh token rõ chỉ xuất hiện ở phía client, không được lưu trực tiếp.
     */
    @Test
    void issueShouldStoreOnlyHashedRefreshTokenAndBuildCompleteResponse() {
        UserAccount user = activeUser();
        when(jwtTokenService.createAccessToken(user)).thenReturn("access-token");

        Instant beforeIssue = Instant.now();
        AuthSessionService.IssuedSession session = authSessionService.issue(user, "browser", "127.0.0.1");

        assertThat(session.refreshToken()).isNotBlank().hasSize(64);
        assertThat(session.response().accessToken()).isEqualTo("access-token");
        assertThat(session.response().tokenType()).isEqualTo("Bearer");
        assertThat(session.response().expiresIn()).isEqualTo(900);
        assertThat(session.response().user().id()).isEqualTo(user.getId());
        assertThat(session.response().user().email()).isEqualTo(user.getEmail());
        assertThat(session.response().user().emailVerified()).isTrue();

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken storedToken = tokenCaptor.getValue();
        assertThat(storedToken.getUser()).isSameAs(user);
        assertThat(storedToken.getTokenHash()).isEqualTo(hash(session.refreshToken()));
        assertThat(storedToken.getTokenHash()).doesNotContain(session.refreshToken());
        assertThat(storedToken.getDeviceInfo()).isEqualTo("browser");
        assertThat(storedToken.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(storedToken.getExpiresAt()).isAfter(beforeIssue.plus(Duration.ofDays(6)));
    }

    /**
     * Kịch bản: metadata thiết bị và IP dài hơn kích thước cột database.
     * Given: deviceInfo dài 300 ký tự, ipAddress dài 60 ký tự.
     * When: issue lưu refresh token.
     * Then: deviceInfo được cắt còn 255, IP còn 45 ký tự mà session vẫn được phát bình thường.
     * Ý nghĩa bảo mật/ổn định: header do client kiểm soát không thể làm lỗi insert vì vượt độ dài cột.
     */
    @Test
    void issueShouldTruncateUntrustedSessionMetadataToDatabaseLimits() {
        UserAccount user = activeUser();
        when(jwtTokenService.createAccessToken(user)).thenReturn("access-token");
        String longDevice = "d".repeat(300);
        String longIp = "1".repeat(60);

        authSessionService.issue(user, longDevice, longIp);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getDeviceInfo()).hasSize(255).isEqualTo(longDevice.substring(0, 255));
        assertThat(tokenCaptor.getValue().getIpAddress()).hasSize(45).isEqualTo(longIp.substring(0, 45));
    }

    /**
     * Kịch bản: metadata null/rỗng khi client không gửi User-Agent hoặc IP.
     * Given: deviceInfo chỉ có khoảng trắng và ipAddress null.
     * When: session được phát.
     * Then: cả hai trường được chuẩn hóa thành null thay vì lưu chuỗi vô nghĩa.
     * Ý nghĩa ổn định: dữ liệu phiên nhất quán và tránh xem whitespace như metadata hợp lệ.
     */
    @Test
    void issueShouldNormalizeBlankSessionMetadataToNull() {
        UserAccount user = activeUser();
        when(jwtTokenService.createAccessToken(user)).thenReturn("access-token");

        authSessionService.issue(user, "   ", null);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getDeviceInfo()).isNull();
        assertThat(tokenCaptor.getValue().getIpAddress()).isNull();
    }

    /**
     * Kịch bản: xoay refresh token hợp lệ.
     * Given: bản hash của token cũ tồn tại, chưa bị revoke và còn hạn.
     * When: rotate được gọi.
     * Then: token cũ có revokedAt và được lưu trước khi phát access/refresh token mới khác token cũ.
     * Ý nghĩa bảo mật: refresh-token rotation làm token cũ mất hiệu lực ngay, chống replay.
     */
    @Test
    void rotateShouldRevokeOldTokenAndIssueNewToken() {
        UserAccount user = activeUser();
        RefreshToken oldToken = usableToken(user, "old-token");
        when(refreshTokenRepository.findByTokenHash(hash("old-token"))).thenReturn(Optional.of(oldToken));
        when(jwtTokenService.createAccessToken(user)).thenReturn("new-access-token");

        AuthSessionService.IssuedSession session = authSessionService.rotate(
                "old-token", "new-browser", "10.0.0.1");

        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(session.refreshToken()).isNotEqualTo("old-token");
        assertThat(session.response().accessToken()).isEqualTo("new-access-token");
        verify(refreshTokenRepository).save(oldToken);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    /**
     * Kịch bản: client gửi refresh token chưa từng được server phát.
     * Given: repository không tìm thấy bản hash.
     * When: rotate được gọi.
     * Then: trả INVALID_OR_EXPIRED_TOKEN và không phát JWT/refresh token mới.
     * Ý nghĩa bảo mật: token ngẫu nhiên giả mạo không tạo được session.
     */
    @Test
    void rotateShouldRejectUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(hash("unknown-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authSessionService.rotate("unknown-token", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Refresh token is invalid or expired");

        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(jwtTokenService, auditLogService);
    }

    /**
     * Kịch bản: refresh token đã hết hạn nhưng chưa bị revoke.
     * Given: expiresAt nằm trong quá khứ.
     * When: rotate kiểm tra isUsable.
     * Then: trả lỗi token chung và giữ nguyên token cũ.
     * Ý nghĩa bảo mật: TTL phía server được áp dụng độc lập với cookie phía trình duyệt.
     */
    @Test
    void rotateShouldRejectExpiredToken() {
        RefreshToken expiredToken = usableToken(activeUser(), "expired-token");
        expiredToken.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(hash("expired-token"))).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authSessionService.rotate("expired-token", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN));

        assertThat(expiredToken.getRevokedAt()).isNull();
        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(jwtTokenService, auditLogService);
    }

    /**
     * Kịch bản: refresh token đã bị thu hồi trước đó.
     * Given: revokedAt khác null dù expiresAt vẫn còn tương lai.
     * When: token bị gửi lại để rotate.
     * Then: trả INVALID_OR_EXPIRED_TOKEN và không phát session mới.
     * Ý nghĩa bảo mật: logout/đổi mật khẩu có hiệu lực ngay cả khi cookie cũ chưa hết hạn.
     */
    @Test
    void rotateShouldRejectRevokedToken() {
        RefreshToken revokedToken = usableToken(activeUser(), "revoked-token");
        revokedToken.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByTokenHash(hash("revoked-token"))).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authSessionService.rotate("revoked-token", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Refresh token is invalid or expired");

        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(jwtTokenService, auditLogService);
    }

    /**
     * Kịch bản: logout bằng refresh token hợp lệ.
     * Given: repository tìm thấy token chưa bị thu hồi và có owner.
     * When: logout được gọi.
     * Then: token nhận revokedAt, được lưu và audit LOGOUT_SUCCEEDED gắn đúng user.
     * Ý nghĩa bảo mật: server vô hiệu credential thay vì chỉ dựa vào việc frontend xóa cookie.
     */
    @Test
    void logoutShouldRevokeTokenAndAuditItsOwner() {
        UserAccount user = activeUser();
        RefreshToken refreshToken = usableToken(user, "raw-refresh-token");
        when(refreshTokenRepository.findByTokenHash(hash("raw-refresh-token")))
                .thenReturn(Optional.of(refreshToken));

        authSessionService.logout("raw-refresh-token");

        assertThat(refreshToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(refreshToken);
        verify(auditLogService).recordAuthentication(
                user, user.getEmail(), AuditAction.LOGOUT_SUCCEEDED, AuditResult.SUCCESS,
                "Logout succeeded", null, null, null);
    }

    /**
     * Kịch bản: logout khi client không còn refresh cookie.
     * Given: raw token null hoặc chỉ chứa whitespace.
     * When: logout được gọi.
     * Then: hoàn tất idempotent mà không query database và không tạo audit giả.
     * Ý nghĩa bảo mật/UX: người dùng luôn có thể logout phía client dù session server đã mất.
     */
    @Test
    void logoutShouldBeIdempotentWhenRefreshTokenIsMissing() {
        authSessionService.logout("   ");

        verifyNoInteractions(refreshTokenRepository, jwtTokenService, auditLogService);
    }

    /**
     * Kịch bản: logout bằng token không tồn tại.
     * Given: repository không tìm thấy bản hash.
     * When: logout được gọi.
     * Then: không ném lỗi, không save token và không ghi audit cho user không xác định.
     * Ý nghĩa bảo mật: endpoint logout không tiết lộ token có tồn tại hay không.
     */
    @Test
    void logoutShouldSilentlyIgnoreUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(hash("unknown-token"))).thenReturn(Optional.empty());

        authSessionService.logout("unknown-token");

        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(jwtTokenService, auditLogService);
    }

    /**
     * Kịch bản: thu hồi đồng loạt session sau thay đổi credential.
     * Given: user có ID ổn định và có thể đang đăng nhập trên nhiều thiết bị.
     * When: revokeAll được gọi.
     * Then: repository nhận đúng user ID cùng một thời điểm revoke hiện tại.
     * Ý nghĩa bảo mật: reset/đổi mật khẩu loại bỏ toàn bộ refresh token cũ của tài khoản.
     */
    @Test
    void revokeAllShouldDelegateBulkRevocationForUser() {
        UserAccount user = activeUser();
        Instant beforeRevoke = Instant.now();

        authSessionService.revokeAll(user);

        ArgumentCaptor<Instant> revokedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(user.getId()), revokedAtCaptor.capture());
        assertThat(revokedAtCaptor.getValue()).isAfterOrEqualTo(beforeRevoke);
    }

    private RefreshToken usableToken(UserAccount user, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(300));
        return token;
    }

    private UserAccount activeUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("active@example.com");
        user.setFullName("Active User");
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
