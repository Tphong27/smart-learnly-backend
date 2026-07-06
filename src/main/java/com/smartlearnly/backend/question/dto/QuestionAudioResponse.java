package com.smartlearnly.backend.question.dto;

import java.util.UUID;

public record QuestionAudioResponse(
        UUID questionId,
        String audioUrl,
        String contentType,
        long size,
        String fileName
) {
}
