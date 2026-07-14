package com.smartlearnly.backend.curriculum.dto;

import java.util.UUID;

public record ClassCurriculumEditorResponse(
        UUID classId,
        UUID courseId,
        boolean inherited,
        boolean hasDraft,
        boolean hasPublished,
        ClassCurriculumBindingResponse binding,
        CurriculumMetadataResponse metadata,
        CurriculumVersionResponse curriculum
) {
}
