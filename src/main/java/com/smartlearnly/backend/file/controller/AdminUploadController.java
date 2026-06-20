package com.smartlearnly.backend.file.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import com.smartlearnly.backend.file.service.CourseThumbnailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
@RequestMapping("/api/v1/admin/uploads")
@Tag(name = "Admin Uploads", description = "Administrator-managed upload APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminUploadController {
    private final CourseThumbnailService courseThumbnailService;

    @PostMapping(value = "/course-thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a course thumbnail to Supabase Storage")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thumbnail uploaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File exceeds size limit"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "File type is unsupported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Storage provider unavailable")
    })
    public ApiResponse<CourseThumbnailUploadResponse> uploadCourseThumbnail(
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(
                "Course thumbnail uploaded successfully",
                courseThumbnailService.upload(file)
        );
    }
}
