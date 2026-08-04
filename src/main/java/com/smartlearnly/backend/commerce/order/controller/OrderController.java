package com.smartlearnly.backend.commerce.order.controller;

import com.smartlearnly.backend.commerce.order.dto.OrderResponse;
import com.smartlearnly.backend.commerce.order.dto.OrderSummaryResponse;
import com.smartlearnly.backend.commerce.entity.OrderStatus;
import com.smartlearnly.backend.commerce.order.service.OrderService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Checkout and order APIs")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "List orders for admin monitoring")
    // Trả danh sách đơn có phân trang và bộ lọc cho màn hình giám sát của Admin/TMO.
    public ApiResponse<PageResponse<OrderSummaryResponse>> listOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) OrderStatus status
    ) {
        return ApiResponse.success(
                "Orders loaded successfully",
                orderService.listOrders(page, size, keyword, status)
        );
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get order detail")
    // Trả chi tiết đơn nếu người gọi là chủ đơn hoặc có quyền quản trị.
    public ApiResponse<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.success("Order loaded successfully", orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Cancel a pending owned order")
    // Yêu cầu hủy một đơn đang chờ thanh toán thuộc về học viên hiện tại.
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID orderId) {
        return ApiResponse.success("Order cancelled successfully", orderService.cancel(orderId));
    }
}
