package com.smartlearnly.backend.course.preview.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.preview.dto.PreviewAssignmentResponse;
import com.smartlearnly.backend.course.preview.service.PreviewAssignmentService;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.service.LearningContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartlearnly.backend.course.preview.dto.PreviewTestQuestionResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardPracticeSetResponse;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CoursePreviewController {
    private final LearningContentService learningContentService;
    private final PreviewAssignmentService previewAssignmentService;

    // Trả nội dung mẫu công khai để khách xem khóa học trước khi đăng ký.
    // @GetMapping("/{courseId}/preview")
    // public ApiResponse<LearningContentResponse> getPreviewContent(@PathVariable
    // UUID courseId) {
    // return ApiResponse.success("Preview content loaded successfully",
    // learningContentService.getPreviewContent(courseId));
    // }

    @GetMapping("/{courseId}/preview")
    public ApiResponse<LearningContentResponse> getPreviewContent(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID classId) {

        return ApiResponse.success(
                "Preview content loaded successfully",
                learningContentService.getPreviewContent(courseId, classId));
    }

    /**
     * Trả danh sách câu hỏi chỉ đọc cho lesson QUIZ được đánh dấu preview.
     *
     * Không tạo attempt, không trả đáp án đúng và không cho phép submit.
     */
    @GetMapping("/{courseId}/preview-lessons/{lessonId}/questions")
    public ApiResponse<List<PreviewTestQuestionResponse>> getPreviewTestQuestions(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {

        return ApiResponse.success(
                "Preview test questions loaded successfully",
                learningContentService.getPreviewTestQuestions(
                        courseId,
                        classId,
                        lessonId));
    }

    /**
     * Trả bộ flashcard chỉ đọc của lesson được đánh dấu preview.
     */
    @GetMapping("/{courseId}/preview-lessons/{lessonId}/flashcards")
    public ApiResponse<FlashcardPracticeSetResponse> getPreviewFlashcards(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {

        return ApiResponse.success(
                "Preview flashcards loaded successfully",
                learningContentService.getPreviewFlashcards(
                        courseId,
                        classId,
                        lessonId));
    }

    /** Trả nội dung assignment chỉ đọc cho lesson được đánh dấu preview. */
    @GetMapping("/{courseId}/preview-lessons/{lessonId}/assignment")
    public ApiResponse<PreviewAssignmentResponse> getPreviewAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success(
                "Preview assignment loaded successfully",
                previewAssignmentService.getPreviewAssignment(courseId, classId, lessonId));
    }
}
