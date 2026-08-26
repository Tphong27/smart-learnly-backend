package com.smartlearnly.backend.course.preview.dto;

import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import java.util.List;

public record PreviewTestAnswerResponse(
        String answerText,
        Integer displayOrder,
        List<QuestionAnswerMediaResponse> media
) {
}