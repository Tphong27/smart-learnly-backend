package com.smartlearnly.backend.auth.seed;

import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.service.AuthService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "auth-users-enabled", havingValue = "true", matchIfMissing = true)
public class AuthDevDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AuthDevDataSeeder.class);

    private static final String ACTIVE_EMAIL = "active.trainee@smartlearnly.dev";
    private static final String ACTIVE_PASSWORD = "Active@123";
    private static final String PENDING_EMAIL = "pending.trainee@smartlearnly.dev";
    private static final String PENDING_PASSWORD = "Pending@123";
    private static final String GOOGLE_EMAIL = "google.user@smartlearnly.dev";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedActiveUser();
        seedPendingUser();
        seedGoogleOnlyUser();

        authService.forgotPassword(new ForgotPasswordRequest(ACTIVE_EMAIL));
        authService.resendVerification(new ResendVerificationRequest(PENDING_EMAIL));

        log.info("Dev auth seed ready");
        log.info("Seeded user: email={} password={}", ACTIVE_EMAIL, ACTIVE_PASSWORD);
        log.info("Seeded user: email={} password={}", PENDING_EMAIL, PENDING_PASSWORD);
        log.info("Seeded user: email={} password=<google-only>", GOOGLE_EMAIL);
        log.info("Password reset token for {} and verification OTP for {} were generated.", ACTIVE_EMAIL, PENDING_EMAIL);
    }

    private void seedActiveUser() {
        UserAccount user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(ACTIVE_EMAIL)
                .orElseGet(UserAccount::new);
        user.setEmail(ACTIVE_EMAIL);
        user.setFullName("Active Trainee");
        user.setPasswordHash(passwordEncoder.encode(ACTIVE_PASSWORD));
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setAvatarUrl("https://api.dicebear.com/9.x/initials/svg?seed=Active%20Trainee");
        user.setPhoneNumber("+84901234567");
        user.setBio("Seeded active trainee account for local authentication testing.");
        user.setGoogleId(null);
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordChangedAt(Instant.now());
        user.setDeletedAt(null);
        userRepository.save(user);
    }

    private void seedPendingUser() {
        UserAccount user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(PENDING_EMAIL)
                .orElseGet(UserAccount::new);
        user.setEmail(PENDING_EMAIL);
        user.setFullName("Pending Trainee");
        user.setPasswordHash(passwordEncoder.encode(PENDING_PASSWORD));
        user.setRole("TRAINEE");
        user.setStatus("pending_verify");
        user.setAvatarUrl("https://api.dicebear.com/9.x/initials/svg?seed=Pending%20Trainee");
        user.setPhoneNumber("+84907654321");
        user.setBio("Seeded pending verification account for email verification testing.");
        user.setGoogleId(null);
        user.setEmailVerifiedAt(null);
        user.setPasswordChangedAt(Instant.now());
        user.setDeletedAt(null);
        userRepository.save(user);
    }

    private void seedGoogleOnlyUser() {
        UserAccount user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(GOOGLE_EMAIL)
                .orElseGet(UserAccount::new);
        user.setEmail(GOOGLE_EMAIL);
        user.setFullName("Google Only User");
        user.setPasswordHash(null);
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setAvatarUrl("https://api.dicebear.com/9.x/initials/svg?seed=Google%20Only%20User");
        user.setPhoneNumber(null);
        user.setBio("Seeded social-login-only account for negative password-change tests.");
        user.setGoogleId("seed-google-account");
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordChangedAt(null);
        user.setDeletedAt(null);
        userRepository.save(user);
    }
}
