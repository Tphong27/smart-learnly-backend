package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.service.CourseContentAdminService;
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
@Tag(name = "Admin Course Content", description = "Administrator section and lesson authoring APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCourseContentController {
    private final CourseContentAdminService courseContentAdminService;

    @GetMapping("/courses/{courseId}/sections")
    @Operation(summary = "List course sections")
    public ApiResponse<List<SectionResponse>> listSections(@PathVariable UUID courseId) {
        return ApiResponse.success("Sections loaded successfully", courseContentAdminService.listSections(courseId));
    }

    @PostMapping("/courses/{courseId}/sections")
    @Operation(summary = "Create a course section")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID courseId,
            @Valid @RequestBody SectionRequest request
    ) {
        SectionResponse section = courseContentAdminService.createSection(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/sections/" + section.id()))
                .body(ApiResponse.success("Section created successfully", section));
    }

    @PutMapping("/courses/{courseId}/sections/order")
    @Operation(summary = "Reorder all course sections")
    public ApiResponse<List<SectionResponse>> reorderSections(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Sections reordered successfully", courseContentAdminService.reorderSections(courseId, request));
    }

    @GetMapping("/sections/{sectionId}")
    @Operation(summary = "Get section details")
    public ApiResponse<SectionResponse> getSection(@PathVariable UUID sectionId) {
        return ApiResponse.success("Section loaded successfully", courseContentAdminService.getSection(sectionId));
    }

    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "Update a section")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID sectionId,
            @Valid @RequestBody SectionRequest request
    ) {
        return ApiResponse.success("Section updated successfully", courseContentAdminService.updateSection(sectionId, request));
    }

    @DeleteMapping("/sections/{sectionId}")
    @Operation(summary = "Delete a section and its lessons")
    public ApiResponse<Void> deleteSection(@PathVariable UUID sectionId) {
        courseContentAdminService.deleteSection(sectionId);
        return ApiResponse.success("Section deleted successfully");
    }

    @GetMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "List section lessons")
    public ApiResponse<List<LessonResponse>> listLessons(@PathVariable UUID sectionId) {
        return ApiResponse.success("Lessons loaded successfully", courseContentAdminService.listLessons(sectionId));
    }

    @PostMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "Create a lesson")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID sectionId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = courseContentAdminService.createLesson(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PutMapping("/sections/{sectionId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a section")
    public ApiResponse<List<LessonResponse>> reorderLessons(
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Lessons reordered successfully", courseContentAdminService.reorderLessons(sectionId, request));
    }

    @GetMapping("/lessons/{lessonId}")
    @Operation(summary = "Get lesson details")
    public ApiResponse<LessonResponse> getLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success("Lesson loaded successfully", courseContentAdminService.getLesson(lessonId));
    }

    @PutMapping("/lessons/{lessonId}")
    @Operation(summary = "Update a lesson")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request
    ) {
        return ApiResponse.success("Lesson updated successfully", courseContentAdminService.updateLesson(lessonId, request));
    }

    @DeleteMapping("/lessons/{lessonId}")
    @Operation(summary = "Deactivate a lesson")
    public ApiResponse<Void> deleteLesson(@PathVariable UUID lessonId) {
        courseContentAdminService.deleteLesson(lessonId);
        return ApiResponse.success("Lesson deactivated successfully");
    }
}
