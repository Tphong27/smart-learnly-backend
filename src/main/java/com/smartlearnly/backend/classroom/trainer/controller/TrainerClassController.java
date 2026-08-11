package com.smartlearnly.backend.classroom.trainer.controller;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.trainer.service.ClassTrainerService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TrainerClassController {
    private final ClassTrainerService classTrainerService;

    // Liệt kê các lớp đã phân công cho giảng viên đang đăng nhập.
    @GetMapping("/trainer/classes")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<PageResponse<ClassResponse>> listMyAssignedClasses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                "Assigned classes loaded successfully",
                classTrainerService.listMyAssignedClasses(status, keyword, courseId, page, size));
    }

    // Trả chi tiết lớp chỉ khi lớp đó được phân công cho giảng viên hiện tại.
    @GetMapping("/trainer/classes/{classId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<ClassResponse> getMyAssignedClassDetail(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Assigned class loaded successfully",
                classTrainerService.getMyAssignedClassDetail(classId));
    }
}
