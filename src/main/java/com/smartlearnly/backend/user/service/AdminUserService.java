package com.smartlearnly.backend.user.service;

import com.smartlearnly.backend.user.dto.AdminUserPageResponse;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.dto.CreateAdminUserRequest;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_STATUS = "active";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
        }

        UserAccount user = new UserAccount();
        String role = normalizeRole(request.role());
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhoneNumber(normalizeNullable(request.phoneNumber()));
        user.setRole(role == null ? "TRAINEE" : role);
        user.setStatus(normalizeNullable(request.status()) == null
                ? DEFAULT_STATUS
                : request.status().trim().toLowerCase(Locale.ROOT));
        user.setEmailVerifiedAt(Boolean.TRUE.equals(request.emailVerified()) ? Instant.now() : null);
        user.setPasswordHash(passwordEncoder.encode(generateTemporaryPassword()));
        user.setFailedLoginAttempts(0);

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse list(String role, String status, String keyword, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizeSize(size);
        Page<UserAccount> users = userRepository.searchAdminUsers(
                normalizeRole(role),
                normalizeStatus(status),
                normalizeKeyword(keyword),
                PageRequest.of(normalizedPage, normalizedSize)
        );
        return new AdminUserPageResponse(
                users.stream().map(this::toResponse).toList(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return size;
    }

    private String normalizeRole(String role) {
        String normalized = normalizeNullable(role);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullable(status);
        return normalized == null ? DEFAULT_STATUS : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = normalizeNullable(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String generateTemporaryPassword() {
        return "Aa1!" + UUID.randomUUID();
    }

    private AdminUserResponse toResponse(UserAccount user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );
    }
}
