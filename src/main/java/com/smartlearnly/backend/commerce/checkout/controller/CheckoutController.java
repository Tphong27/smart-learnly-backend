package com.smartlearnly.backend.commerce.checkout.controller;

import com.smartlearnly.backend.commerce.checkout.dto.CheckoutRequest;
import com.smartlearnly.backend.commerce.checkout.dto.CheckoutResponse;
import com.smartlearnly.backend.commerce.checkout.service.CheckoutService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@Tag(name = "Checkout", description = "Checkout creation APIs")
@SecurityRequirement(name = "bearerAuth")
public class CheckoutController {
    private final CheckoutService checkoutService;

    /** Tạo một checkout có tính phí cho khóa học online hoặc lớp học offline. */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Checkout one course or class directly")
    public ApiResponse<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success(
                "Checkout created successfully",
                checkoutService.checkout(request.itemType(), request.courseId(), request.classId())
        );
    }
}
