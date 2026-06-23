package com.smartlearnly.backend.user.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.dto.CreateAdminUserRequest;
import com.smartlearnly.backend.user.dto.UpdateAdminUserRequest;
import com.smartlearnly.backend.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Administrator user and role management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List users for admin")
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success("Users loaded successfully", adminUserService.list(role, status, keyword, page, size));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get admin user detail")
    public ApiResponse<AdminUserResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success("User loaded successfully", adminUserService.get(userId));
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<ApiResponse<AdminUserResponse>> create(@Valid @RequestBody CreateAdminUserRequest request) {
        AdminUserResponse created = adminUserService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id()))
                .body(ApiResponse.success("User created successfully", created));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update selected user fields")
    public ApiResponse<AdminUserResponse> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAdminUserRequest request
    ) {
        return ApiResponse.success("User updated successfully", adminUserService.update(userId, request));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Soft delete a user")
    public ApiResponse<Void> delete(@PathVariable UUID userId) {
        adminUserService.delete(userId);
        return ApiResponse.success("User deleted successfully");
    }
}
