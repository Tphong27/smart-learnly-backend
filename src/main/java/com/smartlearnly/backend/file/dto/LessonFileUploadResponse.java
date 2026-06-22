package com.smartlearnly.backend.file.dto;

public record LessonFileUploadResponse(
        String url,
        String objectPath,
        String fileName,
        long fileSize,
        String contentType
) {
}
