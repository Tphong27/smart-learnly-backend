package com.smartlearnly.backend.course.preview.dto;

import java.util.List;

public record PreviewTestQuestionResponse(
        Integer orderIndex,
        String questionText,
        String imageUrl,
        String audioUrl,
        String questionType,
        List<PreviewTestAnswerResponse> answers
) {
}