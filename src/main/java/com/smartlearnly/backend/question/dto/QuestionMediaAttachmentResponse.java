package com.smartlearnly.backend.question.dto;

import java.time.Instant;
import java.util.UUID;

public record QuestionMediaAttachmentResponse(
        UUID attachmentId,
        UUID id,
        UUID questionId,
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
