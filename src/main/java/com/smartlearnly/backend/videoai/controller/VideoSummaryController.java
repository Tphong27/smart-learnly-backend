package com.smartlearnly.backend.videoai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryResponse;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/video-summary")
@PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
public class VideoSummaryController {

    private final VideoSummaryService service;

    @PostMapping("/generate")
    public ApiResponse<GenerateSummaryResponse> handleGenerateVideoSummaryRequest(
            @Valid @RequestBody GenerateSummaryRequest request) {
        return ApiResponse.success(
                "Video summary generated",
                service.generateVideoSummary(request.youtubeUrl())
        );
    }
}
