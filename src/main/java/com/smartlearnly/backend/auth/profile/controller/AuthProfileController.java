package com.smartlearnly.backend.auth.profile.controller;

import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.profile.service.AuthProfileService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import com.smartlearnly.backend.file.service.CourseThumbnailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthProfileController {
    private final AuthProfileService profileService;
    private final CourseThumbnailService courseThumbnailService;

    @GetMapping("/profile")
    // Trả hồ sơ của người dùng đang đăng nhập.
    public ApiResponse<UserProfileResponse> getProfile() {
        return ApiResponse.success("Profile loaded successfully", profileService.getCurrentUserProfile());
    }

    @PatchMapping("/profile")
    // Cập nhật các trường hồ sơ được cung cấp và giữ nguyên các trường còn lại.
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(
                "Profile updated successfully",
                profileService.updateCurrentUserProfile(request)
        );
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // Lưu ảnh avatar qua file service rồi cập nhật URL vào hồ sơ hiện tại.
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        CourseThumbnailUploadResponse uploaded = courseThumbnailService.upload(file);
        UserProfileResponse updated = profileService.updateCurrentUserProfile(
                new UpdateProfileRequest(null, uploaded.url(), null, null)
        );
        return ApiResponse.success("Avatar uploaded successfully", updated);
    }
}
