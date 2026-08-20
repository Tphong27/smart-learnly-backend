package com.smartlearnly.backend.commerce.order.controller;

import com.smartlearnly.backend.commerce.order.dto.OrderResponse;
import com.smartlearnly.backend.commerce.order.dto.OrderSummaryResponse;
import com.smartlearnly.backend.commerce.entity.OrderStatus;
import com.smartlearnly.backend.commerce.order.service.OrderService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasRole('TMO')")
    // Tr? danh sách đơn có phân trang và b? l?c cho màn h?nh giám sát c?a Admin/TMO.
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
    @PreAuthorize("hasAnyRole('TRAINEE', 'TMO')")
    // Tr? chi ti?t đơn n?u ngư?i g?i là ch? đơn ho?c có quy?n qu?n tr?.
    public ApiResponse<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.success("Order loaded successfully", orderService.getOrder(orderId));
    }

}
