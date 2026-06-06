package com.smartlearnly.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Schema(example = "Nguyen Van A")
        @Pattern(regexp = "^(?!\\s*$).+$", message = "Full name must not be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Schema(example = "https://cdn.example.com/avatar.png")
        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        String avatarUrl,

        @Schema(example = "+84901234567")
        @Pattern(
                regexp = "^\\+?[0-9]{9,15}$",
                message = "Phone number must contain 9 to 15 digits and may start with +"
        )
        String phoneNumber,

        @Schema(example = "Learner profile updated from Swagger UI.")
        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio
) {
}
