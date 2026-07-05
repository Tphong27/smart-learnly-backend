package com.smartlearnly.backend.question.image;

import java.util.List;

public record ImageImportRequest(
        List<ImageImportFile> files,
        String language
) {
}
