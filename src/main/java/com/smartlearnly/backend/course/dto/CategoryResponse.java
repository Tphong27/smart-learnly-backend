package com.smartlearnly.backend.course.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        UUID parentId,
        @JsonProperty("isActive")
        boolean isActive,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
