package com.smartlearnly.backend.curriculum.admin.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.admin.service.CurriculumLessonAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Course Content", description = "Administrator lesson authoring APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCourseLessonController {
    private final CurriculumLessonAdminService curriculumLessonAdminService;

    // Trả danh sách lesson của một section theo thứ tự hiện tại.
    @GetMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "List section lessons")
    public ApiResponse<List<LessonResponse>> listLessons(@PathVariable UUID sectionId) {
        return ApiResponse.success("Lessons loaded successfully", curriculumLessonAdminService.listLessons(sectionId));
    }

    // Trả lesson của module theo route có course ID để giữ tương thích client cũ.
    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    @Operation(summary = "List module lessons")
    public ApiResponse<List<LessonResponse>> listModuleLessons(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId) {
        return ApiResponse.success("Lessons loaded successfully", curriculumLessonAdminService.listModuleLessons(moduleId));
    }

    // Trả lesson của module theo route rút gọn hiện tại.
    @GetMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "List module lessons")
    public ApiResponse<List<LessonResponse>> listModuleLessons(@PathVariable UUID moduleId) {
        return ApiResponse.success("Lessons loaded successfully", curriculumLessonAdminService.listModuleLessons(moduleId));
    }

    // Tạo lesson trong section và trả URL resource vừa tạo.
    @PostMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "Create a lesson")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID sectionId,
            @Valid @RequestBody LessonRequest request) {
        LessonResponse lesson = curriculumLessonAdminService.createLesson(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    // Tạo lesson trong module theo route có course ID để giữ contract cũ.
    @PostMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    @Operation(summary = "Create a lesson in a module")
    public ResponseEntity<ApiResponse<LessonResponse>> createModuleLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request) {
        return createdModuleLesson(moduleId, request);
    }

    // Tạo lesson trong module theo route rút gọn hiện tại.
    @PostMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "Create a lesson in a module")
    public ResponseEntity<ApiResponse<LessonResponse>> createModuleLesson(
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request) {
        return createdModuleLesson(moduleId, request);
    }

    // Lưu thứ tự lesson mới trong section.
    @PutMapping("/sections/{sectionId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a section")
    public ApiResponse<List<LessonResponse>> reorderLessons(
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.success(
                "Lessons reordered successfully",
                curriculumLessonAdminService.reorderLessons(sectionId, request));
    }

    // Lưu thứ tự lesson module theo route có course ID để giữ contract cũ.
    @PutMapping("/courses/{courseId}/modules/{moduleId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a module")
    public ApiResponse<List<LessonResponse>> reorderModuleLessons(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody ReorderRequest request) {
        return reorderedModuleLessons(moduleId, request);
    }

    // Lưu thứ tự lesson module theo route rút gọn hiện tại.
    @PutMapping("/modules/{moduleId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a module")
    public ApiResponse<List<LessonResponse>> reorderModuleLessons(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ReorderRequest request) {
        return reorderedModuleLessons(moduleId, request);
    }

    // Trả chi tiết lesson mà người dùng có quyền đọc.
    @GetMapping("/lessons/{lessonId}")
    @Operation(summary = "Get lesson details")
    public ApiResponse<LessonResponse> getLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success("Lesson loaded successfully", curriculumLessonAdminService.getLesson(lessonId));
    }

    // Cập nhật nội dung, loại, trạng thái và resource của lesson.
    @PutMapping("/lessons/{lessonId}")
    @Operation(summary = "Update a lesson")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request) {
        return ApiResponse.success(
                "Lesson updated successfully",
                curriculumLessonAdminService.updateLesson(lessonId, request));
    }

    // Vô hiệu lesson theo quy tắc authoring hiện tại.
    @DeleteMapping("/lessons/{lessonId}")
    @Operation(summary = "Deactivate a lesson")
    public ApiResponse<Void> deleteLesson(@PathVariable UUID lessonId) {
        curriculumLessonAdminService.deleteLesson(lessonId);
        return ApiResponse.success("Lesson deactivated successfully");
    }

    // Dùng chung response tạo lesson cho hai route module tương thích.
    private ResponseEntity<ApiResponse<LessonResponse>> createdModuleLesson(
            UUID moduleId,
            LessonRequest request) {
        LessonResponse lesson = curriculumLessonAdminService.createModuleLesson(moduleId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    // Dùng chung response reorder lesson cho hai route module tương thích.
    private ApiResponse<List<LessonResponse>> reorderedModuleLessons(
            UUID moduleId,
            ReorderRequest request) {
        return ApiResponse.success(
                "Lessons reordered successfully",
                curriculumLessonAdminService.reorderModuleLessons(moduleId, request));
    }
}
