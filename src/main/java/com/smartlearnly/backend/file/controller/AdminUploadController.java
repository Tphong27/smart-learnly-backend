package com.smartlearnly.backend.file.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import com.smartlearnly.backend.file.dto.LessonFileUploadResponse;
import com.smartlearnly.backend.file.service.CourseThumbnailService;
import com.smartlearnly.backend.file.service.LessonFileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
// Nới cho TRAINER để trainer có thể upload lesson material / resource / media
// khi tùy biến curriculum của class draft.
@PreAuthorize("hasAnyRole('SME', 'TRAINER')")
@RequestMapping("/api/v1/admin/uploads")
public class AdminUploadController {
        private final CourseThumbnailService courseThumbnailService;
        private final LessonFileUploadService lessonFileUploadService;

        /** Tải thumbnail course cho các role được quản lý hoặc author course detail. */
        @PostMapping(value = "/course-thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
        public ApiResponse<CourseThumbnailUploadResponse> uploadCourseThumbnail(
                        @RequestPart("file") MultipartFile file) {
                return ApiResponse.success(
                                "Course thumbnail uploaded successfully",
                                courseThumbnailService.upload(file));
        }

        /** Tải material chính của lesson cho authoring workflow. */
        @PostMapping(value = "/lesson-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<LessonFileUploadResponse> uploadLessonMaterial(
                        @RequestPart("file") MultipartFile file) {
                return ApiResponse.success(
                                "Lesson material uploaded successfully",
                                lessonFileUploadService.uploadMaterial(file));
        }

        /** Tải resource bổ sung của lesson cho authoring workflow. */
        @PostMapping(value = "/lesson-resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<LessonFileUploadResponse> uploadLessonResource(
                        @RequestPart("file") MultipartFile file) {
                return ApiResponse.success(
                                "Lesson resource uploaded successfully",
                                lessonFileUploadService.uploadResource(file));
        }
}
