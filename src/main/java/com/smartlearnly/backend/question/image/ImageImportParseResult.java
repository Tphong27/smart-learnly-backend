package com.smartlearnly.backend.question.image;

import com.smartlearnly.backend.question.dto.QuestionImageImportDtos;
import java.util.List;

public record ImageImportParseResult(
        String ocrText,
        List<QuestionImageImportDtos.PreviewQuestion> questions,
        List<String> warnings
) {
}
