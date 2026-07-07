package com.smartlearnly.backend.commerce.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID courseId,
        @NotNull UUID classId
) {
}