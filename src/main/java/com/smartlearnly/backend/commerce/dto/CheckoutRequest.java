package com.smartlearnly.backend.commerce.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull CheckoutItemType itemType,
        @NotNull UUID courseId,
        UUID classId
) {
}