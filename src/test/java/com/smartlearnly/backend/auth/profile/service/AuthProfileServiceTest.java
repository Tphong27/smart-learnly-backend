package com.smartlearnly.backend.auth.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthProfileServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private AuthProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new AuthProfileService(userRepository, currentUserService, auditLogService);
    }

    /**
     * Kịch bản: người dùng đã xác thực đọc hồ sơ hiện tại.
     * Given: CurrentUserService trả entity đầy đủ của principal hiện tại.
     * When: getCurrentUserProfile được gọi.
     * Then: mọi trường công khai được ánh xạ đúng sang DTO, bao gồm trạng thái xác thực và timestamps,
     * nhưng service không ghi database hoặc audit vì đây là thao tác read-only.
     * Ý nghĩa bảo mật: response chỉ sử dụng user đã resolve từ security context, không nhận user ID từ client.
     */
    @Test
    void getProfileShouldMapEveryPublicFieldFromAuthenticatedUser() {
        UserAccount user = createUser();
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setPhoneNumber("+84987654321");
        user.setBio("Learner bio");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);

        UserProfileResponse response = profileService.getCurrentUserProfile();

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.fullName()).isEqualTo("Student");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.phoneNumber()).isEqualTo("+84987654321");
        assertThat(response.bio()).isEqualTo("Learner bio");
        assertThat(response.role()).isEqualTo("TRAINEE");
        assertThat(response.status()).isEqualTo("active");
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.emailVerifiedAt()).isEqualTo(user.getEmailVerifiedAt());
        assertThat(response.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(user.getUpdatedAt());
        verifyNoInteractions(userRepository, auditLogService);
    }

    /**
     * Kịch bản: PATCH hồ sơ chỉ cung cấp một số trường.
     * Given: request có fullName/phone/bio nhưng avatarUrl null và user đã có avatar cũ.
     * When: updateCurrentUserProfile được gọi.
     * Then: trường được gửi được trim/cập nhật, avatar không gửi được giữ nguyên, user được lưu,
     * response phản ánh dữ liệu mới và audit PROFILE_UPDATED được ghi.
     * Ý nghĩa sản phẩm: PATCH không được xóa âm thầm các trường mà client không truyền.
     */
    @Test
    void updateProfileShouldPersistProvidedFieldsAndPreserveAbsentFields() {
        UserAccount user = createUser();
        user.setAvatarUrl("https://example.com/original.png");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = profileService.updateCurrentUserProfile(
                new UpdateProfileRequest("  Updated Name  ", null, "  +84987654321  ", "  New bio  "));

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/original.png");
        assertThat(user.getPhoneNumber()).isEqualTo("+84987654321");
        assertThat(user.getBio()).isEqualTo("New bio");
        assertThat(response.fullName()).isEqualTo("Updated Name");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/original.png");
        verify(userRepository).save(user);
        verify(auditLogService).record(
                "student@example.com", "PROFILE_UPDATED", "USER", user.getId().toString());
    }

    /**
     * Kịch bản: client chủ động gửi chuỗi rỗng cho các trường hồ sơ tùy chọn.
     * Given: avatar/phone/bio chỉ chứa whitespace.
     * When: updateCurrentUserProfile chuẩn hóa request.
     * Then: các trường tùy chọn được đổi thành null thay vì lưu whitespace, còn fullName null được giữ nguyên.
     * Ý nghĩa dữ liệu: chuỗi rỗng có một biểu diễn thống nhất là null và không làm bẩn database.
     */
    @Test
    void updateProfileShouldNormalizeBlankOptionalFieldsToNull() {
        UserAccount user = createUser();
        user.setAvatarUrl("old-avatar");
        user.setPhoneNumber("old-phone");
        user.setBio("old-bio");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = profileService.updateCurrentUserProfile(
                new UpdateProfileRequest(null, "   ", " ", "\t"));

        assertThat(user.getFullName()).isEqualTo("Student");
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getBio()).isNull();
        assertThat(response.avatarUrl()).isNull();
        assertThat(response.phoneNumber()).isNull();
        assertThat(response.bio()).isNull();
        verify(userRepository).save(user);
    }

    /**
     * Kịch bản: PATCH không cung cấp bất kỳ trường nào.
     * Given: cả bốn giá trị trong UpdateProfileRequest đều null.
     * When: service kiểm tra changed flag.
     * Then: trả INVALID_REQUEST, không lưu user và không ghi audit.
     * Ý nghĩa API: ngăn một request no-op tạo write/audit gây hiểu nhầm là hồ sơ đã thay đổi.
     */
    @Test
    void updateProfileShouldRejectRequestWithoutAnyProvidedField() {
        UserAccount user = createUser();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);

        assertThatThrownBy(() -> profileService.updateCurrentUserProfile(
                new UpdateProfileRequest(null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage("At least one profile field must be provided");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    private UserAccount createUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setFullName("Student");
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(Instant.now().minusSeconds(3600));
        user.setUpdatedAt(Instant.now().minusSeconds(60));
        return user;
    }
}
