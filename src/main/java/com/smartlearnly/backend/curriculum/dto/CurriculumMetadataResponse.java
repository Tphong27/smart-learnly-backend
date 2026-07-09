package com.smartlearnly.backend.curriculum.dto;

import java.util.UUID;

public record CurriculumMetadataResponse(
        UUID curriculumVersionId,
        String curriculumScope,
        UUID courseId,
        UUID classId,
        boolean customized,
        String source
) {
}
