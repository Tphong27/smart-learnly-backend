package com.smartlearnly.backend.user.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.user.dto.AdminUserPageResponse;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.dto.CreateAdminUserRequest;
import com.smartlearnly.backend.user.dto.UpdateAdminUserRequest;
import com.smartlearnly.backend.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @PostMapping
    public ApiResponse<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request) {
        return ApiResponse.success(
                "User created successfully. The user can set a password with Forgot password.",
                adminUserService.create(request)
        );
    }

    @GetMapping
    public ApiResponse<AdminUserPageResponse> list(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                "Users loaded successfully",
                adminUserService.list(role, status, keyword, page, size)
        );
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success(
                "User loaded successfully",
                adminUserService.get(userId)
        );
    }

    @PatchMapping("/{userId}")
    public ApiResponse<AdminUserResponse> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAdminUserRequest request
    ) {
        return ApiResponse.success(
                "User updated successfully",
                adminUserService.update(userId, request)
        );
    }
}
