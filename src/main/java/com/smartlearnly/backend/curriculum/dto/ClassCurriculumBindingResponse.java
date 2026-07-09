package com.smartlearnly.backend.curriculum.dto;

import java.time.Instant;
import java.util.UUID;

public record ClassCurriculumBindingResponse(
        UUID id,
        UUID classId,
        UUID courseId,
        UUID baseMasterVersionId,
        UUID draftVersionId,
        UUID publishedVersionId,
        String customizationState,
        Instant createdAt,
        Instant updatedAt
) {
}
