package com.smartlearnly.backend.user.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.user.dto.PublicTrainerProfileResponse;
import com.smartlearnly.backend.user.service.PublicTrainerProfileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/trainers")
public class PublicTrainerController {
    private final PublicTrainerProfileService publicTrainerProfileService;

    @GetMapping("/{trainerId}/profile")
    public ApiResponse<PublicTrainerProfileResponse> getPublicTrainerProfile(
            @PathVariable UUID trainerId
    ) {
        return ApiResponse.success(
                "Trainer profile loaded successfully",
                publicTrainerProfileService.getPublicTrainerProfile(trainerId)
        );
    }
}
