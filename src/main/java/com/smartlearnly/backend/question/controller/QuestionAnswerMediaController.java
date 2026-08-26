package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.service.QuestionAnswerMediaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/questions/{questionId}/answers/{answerId}/media")
@PreAuthorize("hasAnyRole('SME', 'TRAINER')")
public class QuestionAnswerMediaController {

    private final QuestionAnswerMediaService answerMediaService;

    @GetMapping
    public ApiResponse<List<QuestionAnswerMediaResponse>> list(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId
    ) {
        return ApiResponse.success("Answer media loaded successfully",
                answerMediaService.list(questionId, answerId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<QuestionAnswerMediaResponse> upload(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId,
            @RequestParam String mediaType,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success("Answer media uploaded successfully",
                answerMediaService.upload(questionId, answerId, mediaType, file));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId,
            @PathVariable UUID attachmentId
    ) {
        answerMediaService.delete(questionId, answerId, attachmentId);
        return ApiResponse.success("Answer media removed successfully");
    }
}
