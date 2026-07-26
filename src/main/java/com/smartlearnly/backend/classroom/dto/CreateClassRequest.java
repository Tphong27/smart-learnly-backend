package com.smartlearnly.backend.classroom.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClassRequest(
        @NotNull(message = "Course is required") UUID courseId,
        @NotBlank(message = "Class name is required") @Size(min = 3, max = 255, message = "Class name must contain between 3 and 255 characters") String className,
        @NotNull(message = "Trainer is required") UUID trainerId,
        @NotBlank(message = "Google Meet URL is required") @Size(max = 255, message = "Google Meet URL must not exceed 255 characters") String meetingUrl,
        @NotBlank(message = "Class schedule is required") @Size(max = 2000, message = "Class schedule must not exceed 2000 characters") String scheduleDescription,
        @NotNull(message = "Start date is required") @FutureOrPresent(message = "Start date must not be in the past") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @NotNull(message = "Capacity is required") @Positive(message = "Capacity must be greater than 0") @Max(value = 500, message = "Capacity must not exceed 500") Integer maxStudents,
        @NotNull(message = "Class price is required") @DecimalMin(value = "0.0", inclusive = true, message = "Class price must be greater than or equal to 0") @DecimalMax(value = "9999999999.99", message = "Class price is too large") @Digits(integer = 10, fraction = 2, message = "Class price must contain at most 2 decimal places") BigDecimal price) {
}