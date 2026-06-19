package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCourseAccessRequest(
        @NotNull Boolean accessBlocked,
        @Size(max = 2000) String reason
) {
}
