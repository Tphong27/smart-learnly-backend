package com.smartlearnly.backend.question.dto;

import java.time.Instant;
import java.util.UUID;

public record QuestionAnswerMediaResponse(
        UUID attachmentId,
        UUID answerId,
        String mediaType,
        String mediaUrl,
        String objectKey,
        String bucket,
        String contentType,
        long size,
        String fileName,
        int displayOrder,
        String importSource,
        Instant createdAt,
        Instant updatedAt
) {
}