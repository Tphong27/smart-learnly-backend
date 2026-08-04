package com.smartlearnly.backend.curriculum.dto;

import java.util.UUID;

public record LessonResourceResponse(
        UUID id,
        String url,
        String objectPath,
        String name,
        Long fileSize,
        String contentType,
        Integer sortOrder
) {
}
