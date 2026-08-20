package com.smartlearnly.backend.course.preview.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.service.LearningContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
public class AdminLearningPreviewController {
    private final LearningContentService learningContentService;

    // Trả toàn bộ nội dung học để người quản trị xem thử mà không cần ghi danh.
    @GetMapping("/{courseId}/learning-preview")
    public ApiResponse<LearningContentResponse> getAdminLearningPreview(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success("Admin preview loaded",
                learningContentService.getAdminPreviewContent(courseId, classId));
    }
}
