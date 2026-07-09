package com.smartlearnly.backend.curriculum.dto;

import java.util.UUID;

public record CurriculumResourceResponse(
        UUID id,
        UUID sourceResourceId,
        UUID sourceCurriculumResourceId,
        String url,
        String objectPath,
        String name,
        Long fileSize,
        String contentType,
        Integer sortOrder
) {
}
