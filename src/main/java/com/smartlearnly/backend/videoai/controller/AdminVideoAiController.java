package com.smartlearnly.backend.videoai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.FlashcardTargetResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateJobRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateFlashcardsRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.JobResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.PublishContentRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.SaveContentRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.StatusResponse;
import com.smartlearnly.backend.videoai.service.VideoAiAuthoringService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/courses/{courseId}/lessons/{lessonId}/video-ai")
@PreAuthorize("hasAnyRole('ADMIN','TMO','SME','TRAINER')")
public class AdminVideoAiController {
    private final VideoAiAuthoringService service;

    @GetMapping("/status")
    public ApiResponse<StatusResponse> status(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return ApiResponse.success(service.adminStatus(courseId, lessonId));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @PathVariable UUID courseId, @PathVariable UUID lessonId,
            @Valid @RequestBody(required = false) GenerateJobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Video AI job queued", service.createAdminJob(courseId, lessonId, request)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<ApiResponse<JobResponse>> retry(
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Video AI job queued", service.retryAdmin(courseId, lessonId, jobId)));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<JobResponse> job(
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID jobId) {
        return ApiResponse.success(service.getAdminJob(courseId, lessonId, jobId));
    }

    @GetMapping("/contents/current")
    public ApiResponse<ContentResponse> current(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return ApiResponse.success(service.getAdminCurrent(courseId, lessonId));
    }

    @PutMapping("/contents/{contentId}")
    public ApiResponse<ContentResponse> save(
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID contentId,
            @Valid @RequestBody SaveContentRequest request) {
        return ApiResponse.success("Video AI draft saved", service.saveAdmin(courseId, lessonId, contentId, request));
    }

    @PostMapping("/contents/{contentId}/publish")
    public ApiResponse<ContentResponse> publish(
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID contentId,
            @Valid @RequestBody PublishContentRequest request) {
        return ApiResponse.success("Video AI content published",
                service.publishAdmin(courseId, lessonId, contentId, request));
    }

    @GetMapping("/flashcard-targets")
    public ApiResponse<List<FlashcardTargetResponse>> targets(
            @PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return ApiResponse.success(service.adminFlashcardTargets(courseId, lessonId));
    }

    @PostMapping("/contents/{contentId}/flashcard-jobs")
    public ResponseEntity<ApiResponse<JobResponse>> createFlashcardJob(
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID contentId,
            @Valid @RequestBody GenerateFlashcardsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                "Flashcard candidate job queued",
                service.createAdminFlashcardJob(courseId, lessonId, contentId, request)));
    }
}
