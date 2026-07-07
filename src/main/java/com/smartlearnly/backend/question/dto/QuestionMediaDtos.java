package com.smartlearnly.backend.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public final class QuestionMediaDtos {

    private QuestionMediaDtos() {
    }

    public record UploadResponse(
            UUID questionId,
            List<QuestionMediaAttachmentResponse> mediaAttachments
    ) {
    }

    public record ReorderRequest(
            @NotBlank(message = "Media type is required")
            String mediaType,

            @NotEmpty(message = "Attachment IDs are required")
            List<UUID> attachmentIds
    ) {
    }
}
