package com.smartlearnly.backend.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClassRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 255) String className,
        UUID trainerId,
        @Size(max = 2000) String scheduleDescription,
        LocalDate startDate,
        LocalDate endDate,
        @NotNull @Positive Integer maxStudents
) {
}
