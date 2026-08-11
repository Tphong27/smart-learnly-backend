package com.smartlearnly.backend.auth.profile.dto;

import com.smartlearnly.backend.common.validation.PhoneNumberRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Pattern(regexp = "^(?!\\s*$).+$", message = "Full name must not be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        String avatarUrl,

        @Pattern(
                regexp = PhoneNumberRules.VIETNAMESE_MOBILE_PATTERN,
                message = PhoneNumberRules.VIETNAMESE_MOBILE_MESSAGE
        )
        String phoneNumber,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio
) {
}
