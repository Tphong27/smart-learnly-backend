package com.smartlearnly.backend.question.dto;

import java.util.UUID;

public record QuestionImageResponse(
        UUID questionId,
        String imageUrl,
        String contentType,
        long size,
        String fileName
) {
}
