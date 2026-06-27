package com.smartlearnly.backend.user.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.user.dto.PublicTrainerProfileResponse;
import com.smartlearnly.backend.user.service.PublicTrainerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/trainers")
@Tag(name = "Public Trainers", description = "Public trainer profile APIs")
public class PublicTrainerController {
    private final PublicTrainerProfileService publicTrainerProfileService;

    @GetMapping("/{trainerId}/profile")
    @Operation(summary = "Get public trainer profile")
    public ApiResponse<PublicTrainerProfileResponse> getPublicTrainerProfile(
            @PathVariable UUID trainerId
    ) {
        return ApiResponse.success(
                "Trainer profile loaded successfully",
                publicTrainerProfileService.getPublicTrainerProfile(trainerId)
        );
    }
}