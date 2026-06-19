package com.smartlearnly.backend.commerce.controller;

import com.smartlearnly.backend.commerce.dto.CheckoutRequest;
import com.smartlearnly.backend.commerce.dto.CheckoutResponse;
import com.smartlearnly.backend.commerce.dto.OrderResponse;
import com.smartlearnly.backend.commerce.service.CheckoutService;
import com.smartlearnly.backend.commerce.service.OrderService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Checkout and order APIs")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final CheckoutService checkoutService;
    private final OrderService orderService;

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Checkout an authenticated trainee cart")
    public ApiResponse<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success("Checkout created successfully", checkoutService.checkout(request.cartId()));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get order detail")
    public ApiResponse<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.success("Order loaded successfully", orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Cancel a pending owned order")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID orderId) {
        return ApiResponse.success("Order cancelled successfully", orderService.cancel(orderId));
    }
}
