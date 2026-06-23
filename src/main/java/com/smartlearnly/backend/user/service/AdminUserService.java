package com.smartlearnly.backend.user.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.dto.CreateAdminUserRequest;
import com.smartlearnly.backend.user.dto.UpdateAdminUserRequest;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
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
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SUPPORTED_ROLES = Set.of("GUEST", "TRAINEE", "TRAINER", "TMO", "SME", "ADMIN");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("pending_verify", "active", "inactive", "banned");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String role, String status, String keyword, int page, int size) {
        UserSearchCriteria criteria = new UserSearchCriteria(
                normalizeRole(role),
                normalizeStatus(status),
                normalizeKeyword(keyword)
        );

        Page<UserAccount> users = userRepository.findAdminUsers(
                criteria.role(),
                criteria.status(),
                criteria.keyword(),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))
        );

        return new PageResponse<>(
                users.stream().map(AdminUserResponse::from).toList(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return AdminUserResponse.from(findUser(userId));
    }

    @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String email = normalizeEmail(request.email());
        assertEmailAvailable(email, null);

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(normalizeRequired(request.fullName(), "Full name is required"));
        user.setAvatarUrl(normalize(request.avatarUrl()));
        user.setPhoneNumber(normalize(request.phoneNumber()));
        user.setBio(normalize(request.bio()));
        user.setRole(resolveRole(request.role(), "TRAINEE"));
        user.setStatus(resolveStatus(request.status(), "active"));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(Instant.now());
        applyEmailVerifiedFlag(user, request.emailVerified());

        UserAccount saved = userRepository.save(user);
        auditLogService.record(actor.getEmail(), "USER_CREATED", "USER", saved.getId().toString());
        return AdminUserResponse.from(saved);
    }

    @Transactional
    public AdminUserResponse update(UUID userId, UpdateAdminUserRequest request) {
        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one user field must be provided");
        }

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        UserAccount user = findUser(userId);
        boolean updatingSelf = actor.getId().equals(user.getId());

        if (request.fullName() != null) {
            user.setFullName(normalizeRequired(request.fullName(), "Full name must not be blank"));
        }
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            assertEmailAvailable(email, user.getId());
            user.setEmail(email);
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalize(request.avatarUrl()));
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(normalize(request.phoneNumber()));
        }
        if (request.bio() != null) {
            user.setBio(normalize(request.bio()));
        }
        if (request.role() != null) {
            String role = normalizeRole(request.role());
            if (updatingSelf && !"ADMIN".equals(role)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "You cannot remove your own ADMIN role");
            }
            user.setRole(role);
        }
        if (request.status() != null) {
            String status = normalizeStatus(request.status());
            if (updatingSelf && !"active".equals(status)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "You cannot deactivate or ban your own account");
            }
            user.setStatus(status);
        }
        if (request.emailVerified() != null) {
            applyEmailVerifiedFlag(user, request.emailVerified());
        }
        else if ("active".equalsIgnoreCase(user.getStatus()) && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        if (request.password() != null) {
            String password = normalizeRequired(request.password(), "Password must not be blank");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setPasswordChangedAt(Instant.now());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        UserAccount saved = userRepository.save(user);
        auditLogService.record(actor.getEmail(), "USER_UPDATED", "USER", saved.getId().toString());
        return AdminUserResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        UserAccount user = findUser(userId);
        if (actor.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "You cannot delete your own account");
        }

        user.setStatus("inactive");
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        auditLogService.record(actor.getEmail(), "USER_DELETED", "USER", user.getId().toString());
    }

    private UserAccount findUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
    }

    private void assertEmailAvailable(String email, UUID currentUserId) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
                });
    }

    private void applyEmailVerifiedFlag(UserAccount user, Boolean emailVerified) {
        boolean shouldVerify = emailVerified == null
                ? "active".equalsIgnoreCase(user.getStatus())
                : emailVerified;
        user.setEmailVerifiedAt(shouldVerify ? Instant.now() : null);
    }

    private String resolveRole(String value, String defaultRole) {
        String normalized = normalize(value);
        return normalized == null ? defaultRole : normalizeRole(normalized);
    }

    private String resolveStatus(String value, String defaultStatus) {
        String normalized = normalize(value);
        return normalized == null ? defaultStatus : normalizeStatus(normalized);
    }

    private String normalizeRole(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String role = normalized.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "User role is invalid");
        }
        return role;
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String status = normalized.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "User status is invalid");
        }
        return status;
    }

    private String normalizeEmail(String email) {
        return normalizeRequired(email, "Email is required").toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = normalize(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record UserSearchCriteria(String role, String status, String keyword) {
    }
}
