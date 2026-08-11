package com.smartlearnly.backend.course.preview.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.preview.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.preview.service.PreviewLessonService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses/{courseId}/preview-lessons")
public class PreviewLessonController {
    private final PreviewLessonService previewLessonService;

    // Liệt kê các bài học mẫu đã xuất bản của một khóa học công khai.
    @GetMapping
    public ApiResponse<List<PreviewLessonResponse>> list(@PathVariable UUID courseId) {
        return ApiResponse.success("Preview lessons loaded successfully", previewLessonService.listPreviewLessons(courseId));
    }

    // Trả chi tiết một bài học mẫu đã xuất bản theo mã bài học.
    @GetMapping("/{lessonId}")
    public ApiResponse<PreviewLessonResponse> get(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success("Preview lesson loaded successfully", previewLessonService.getPreviewLesson(courseId, lessonId));
    }
}
