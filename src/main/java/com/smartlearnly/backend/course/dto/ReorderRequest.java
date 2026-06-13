package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReorderRequest(
        @NotEmpty(message = "Reorder list must not be empty")
        @Size(max = 200, message = "Reorder list must not exceed 200 items")
        List<@NotNull(message = "Reorder item id must not be null") UUID> ids
) {
}
