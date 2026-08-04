package com.smartlearnly.backend.auth.profile.service;

import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthProfileService {
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    // Trả hồ sơ của người dùng đã xác thực hiện tại.
    public UserProfileResponse getCurrentUserProfile() {
        return toUserProfileResponse(currentUserService.requireAuthenticatedUser());
    }

    @Transactional
    // Cập nhật đúng các trường hồ sơ được gửi lên và ghi audit thay đổi.
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request) {
        UserAccount user = currentUserService.requireAuthenticatedUser();
        boolean changed = false;

        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
            changed = true;
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalizeNullable(request.avatarUrl()));
            changed = true;
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(normalizeNullable(request.phoneNumber()));
            changed = true;
        }
        if (request.bio() != null) {
            user.setBio(normalizeNullable(request.bio()));
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "At least one profile field must be provided");
        }

        UserAccount savedUser = userRepository.save(user);
        auditLogService.record(savedUser.getEmail(), "PROFILE_UPDATED", "USER", savedUser.getId().toString());
        return toUserProfileResponse(savedUser);
    }

    // Chuyển entity người dùng thành DTO hồ sơ an toàn cho frontend.
    private UserProfileResponse toUserProfileResponse(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    // Chuẩn hóa trường hồ sơ tùy chọn, đổi chuỗi rỗng thành null.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
