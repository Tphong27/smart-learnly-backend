package com.smartlearnly.backend.curriculum.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurriculumSectionResponse(
        UUID id,
        UUID sourceSectionId,
        UUID sourceCurriculumSectionId,
        String title,
        Integer sortOrder,
        List<CurriculumLessonResponse> lessons,
        Instant createdAt,
        Instant updatedAt
) {
}
