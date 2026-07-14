package com.smartlearnly.backend.flashcard.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.service.FlashcardImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Flashcard Images", description = "Scoped flashcard image uploads.")
@SecurityRequirement(name = "bearerAuth")
public class AdminFlashcardImageUploadController {
    private final FlashcardImageUploadService flashcardImageUploadService;

    @PostMapping(value = "/flashcard-sets/{setId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image for a flashcard set")
    public ApiResponse<FlashcardImageUploadResponse> upload(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ApiResponse.success(
                "Flashcard image uploaded successfully",
                flashcardImageUploadService.upload(setId, file)
        );
    }
}
