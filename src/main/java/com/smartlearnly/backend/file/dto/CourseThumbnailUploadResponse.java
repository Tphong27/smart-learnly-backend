package com.smartlearnly.backend.file.dto;

public record CourseThumbnailUploadResponse(
        String url,
        String objectPath,
        String fileName,
        String contentType,
        long size
) {
}
