package com.smartlearnly.backend.question.image;

public record ImageImportFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
