package com.smartlearnly.backend.curriculum.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurriculumVersionResponse(
        UUID id,
        UUID courseId,
        UUID classId,
        String scope,
        String status,
        Integer versionNumber,
        String title,
        UUID sourceVersionId,
        UUID createdBy,
        Instant publishedAt,
        Instant archivedAt,
        List<CurriculumSectionResponse> sections,
        Instant createdAt,
        Instant updatedAt
) {
}
