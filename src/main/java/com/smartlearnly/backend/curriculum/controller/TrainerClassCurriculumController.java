package com.smartlearnly.backend.curriculum.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceResponse;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.curriculum.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.dto.ClassCurriculumEditorResponse;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TRAINER', 'ADMIN', 'TMO')")
@RequestMapping("/api/v1/trainer/classes/{classId}/curriculum")
public class TrainerClassCurriculumController {
    private final TrainerClassCurriculumService trainerClassCurriculumService;

    /** Trả curriculum hiệu lực của lớp cho Trainer, Admin hoặc TMO ở chế độ xem. */
    @GetMapping
    public ApiResponse<ClassCurriculumEditorResponse> getCurriculum(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Class curriculum loaded successfully",
                trainerClassCurriculumService.getEditorCurriculum(classId)
        );
    }

    /** Khởi tạo draft lớp; TMO bị loại vì không có quyền author curriculum. */
    @PostMapping("/draft")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ClassCurriculumEditorResponse>> initializeDraft(@PathVariable UUID classId) {
        ClassCurriculumEditorResponse response = trainerClassCurriculumService.initializeDraft(classId);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum"))
                .body(ApiResponse.success("Class curriculum draft initialized successfully", response));
    }

    /** Xuất bản draft curriculum của lớp cho các role author được phép. */
    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<ClassCurriculumEditorResponse> publishDraft(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Class curriculum published successfully",
                trainerClassCurriculumService.publishDraft(classId)
        );
    }

    /** Tạo section mới trong class curriculum draft. */
    @PostMapping("/sections")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID classId,
            @Valid @RequestBody SectionRequest request
    ) {
        SectionResponse section = trainerClassCurriculumService.createSection(classId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/sections/" + section.id()))
                .body(ApiResponse.success("Section created successfully", section));
    }

    /** Lưu thứ tự section mới trong class curriculum draft. */
    @PutMapping("/sections/order")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<List<SectionResponse>> reorderSections(
            @PathVariable UUID classId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Sections reordered successfully",
                trainerClassCurriculumService.reorderSections(classId, request)
        );
    }

    /** Cập nhật section thuộc curriculum draft của lớp. */
    @PutMapping("/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody SectionRequest request
    ) {
        return ApiResponse.success(
                "Section updated successfully",
                trainerClassCurriculumService.updateSection(classId, sectionId, request)
        );
    }

    /** Xóa section thuộc curriculum draft của lớp. */
    @DeleteMapping("/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<Void> deleteSection(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId
    ) {
        trainerClassCurriculumService.deleteSection(classId, sectionId);
        return ApiResponse.success("Section deleted successfully");
    }

    /** Tạo lesson trong section của class curriculum draft. */
    @PostMapping("/sections/{sectionId}/lessons")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = trainerClassCurriculumService.createLesson(classId, sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    /** Lưu thứ tự lesson trong section của lớp. */
    @PutMapping("/sections/{sectionId}/lessons/order")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<List<LessonResponse>> reorderLessons(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Lessons reordered successfully",
                trainerClassCurriculumService.reorderLessons(classId, sectionId, request)
        );
    }

    /** Trả chi tiết lesson trong class curriculum cho role có quyền xem lớp. */
    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonResponse> getLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(
                "Lesson loaded successfully",
                trainerClassCurriculumService.getLesson(classId, lessonId)
        );
    }

    /** Cập nhật lesson trong class curriculum draft. */
    @PutMapping("/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request
    ) {
        return ApiResponse.success(
                "Lesson updated successfully",
                trainerClassCurriculumService.updateLesson(classId, lessonId, request)
        );
    }

    /** Xóa lesson trong class curriculum draft. */
    @DeleteMapping("/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<Void> deleteLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        trainerClassCurriculumService.deleteLesson(classId, lessonId);
        return ApiResponse.success("Lesson deleted successfully");
    }

    /** Thêm resource vào lesson của class curriculum draft. */
    @PostMapping("/lessons/{lessonId}/resources")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LessonResourceResponse>> addResource(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonResourceRequest request
    ) {
        LessonResourceResponse resource = trainerClassCurriculumService.addResource(classId, lessonId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/resources/" + resource.id()))
                .body(ApiResponse.success("Resource added successfully", resource));
    }

    /** Thay toàn bộ resource của lesson trong class curriculum draft. */
    @PutMapping("/lessons/{lessonId}/resources")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<List<LessonResourceResponse>> replaceResources(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody List<@Valid LessonResourceRequest> request
    ) {
        return ApiResponse.success(
                "Resources replaced successfully",
                trainerClassCurriculumService.replaceResources(classId, lessonId, request)
        );
    }

    /** Lưu thứ tự resource của lesson trong class curriculum draft. */
    @PutMapping("/lessons/{lessonId}/resources/order")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<List<LessonResourceResponse>> reorderResources(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Resources reordered successfully",
                trainerClassCurriculumService.reorderResources(classId, lessonId, request)
        );
    }

    /** Xóa resource khỏi lesson trong class curriculum draft. */
    @DeleteMapping("/lessons/{lessonId}/resources/{resourceId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<Void> removeResource(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId
    ) {
        trainerClassCurriculumService.removeResource(classId, lessonId, resourceId);
        return ApiResponse.success("Resource removed successfully");
    }
}
