package com.smartlearnly.backend.auth.seed;

import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "admin-enabled", havingValue = "true")
public class AdminAccountSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:admin@slp.vn}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.admin-full-name:System Administrator}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedEmail = adminEmail.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail).isPresent()) {
            log.info("Initial admin account already exists: {}", normalizedEmail);
            return;
        }
        if (adminPassword == null || adminPassword.length() < 12) {
            throw new IllegalStateException("APP_SEED_ADMIN_PASSWORD must contain at least 12 characters");
        }

        UserAccount admin = new UserAccount();
        admin.setEmail(normalizedEmail);
        admin.setFullName(adminFullName.trim());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setStatus("active");
        admin.setEmailVerifiedAt(Instant.now());
        admin.setPasswordChangedAt(Instant.now());
        admin.setFailedLoginAttempts(0);
        userRepository.save(admin);
        log.info("Initial admin account created: {}", normalizedEmail);
    }
}
