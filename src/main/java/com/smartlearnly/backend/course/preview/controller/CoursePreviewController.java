package com.smartlearnly.backend.course.preview.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.service.LearningContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CoursePreviewController {
    private final LearningContentService learningContentService;

    // Trả nội dung mẫu công khai để khách xem khóa học trước khi đăng ký.
    @GetMapping("/{courseId}/preview")
    public ApiResponse<LearningContentResponse> getPreviewContent(@PathVariable UUID courseId) {
        return ApiResponse.success("Preview content loaded successfully",
                learningContentService.getPreviewContent(courseId));
    }
}
