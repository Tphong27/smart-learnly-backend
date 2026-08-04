package com.smartlearnly.backend.classroom.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RestoreClassRequest(

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate) {
}
