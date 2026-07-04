package com.smartlearnly.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FreeEnrollmentRequest(
        @NotNull UUID courseId,
        @NotNull UUID classId
) {
}