package com.smartlearnly.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FreeCourseEnrollmentRequest(
        @NotNull UUID courseId
) {
}