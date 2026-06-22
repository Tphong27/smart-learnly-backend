package com.smartlearnly.backend.course.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReorderRequest(
        @JsonAlias("orderedIds")
        @NotEmpty(message = "Reorder list must not be empty")
        @Size(max = 300, message = "Reorder list must not exceed 300 items")
        List<@NotNull(message = "Reorder item id must not be null") UUID> ids
) {
}
