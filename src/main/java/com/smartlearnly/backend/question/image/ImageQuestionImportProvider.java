package com.smartlearnly.backend.question.image;

public interface ImageQuestionImportProvider {
    ImageImportParseResult parse(ImageImportRequest request);
}
