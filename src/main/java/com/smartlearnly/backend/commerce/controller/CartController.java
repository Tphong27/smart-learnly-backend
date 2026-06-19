package com.smartlearnly.backend.commerce.controller;

import com.smartlearnly.backend.commerce.dto.AddCartItemRequest;
import com.smartlearnly.backend.commerce.dto.CartResponse;
import com.smartlearnly.backend.commerce.service.CartService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('TRAINEE')")
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Authenticated trainee cart APIs")
@SecurityRequirement(name = "bearerAuth")
public class CartController {
    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get authenticated trainee cart")
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.success("Cart loaded successfully", cartService.getCart());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a course or class item to cart")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.success("Cart item added successfully", cartService.addItem(request));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove a cart item")
    public ApiResponse<Void> removeItem(@PathVariable UUID itemId) {
        cartService.removeItem(itemId);
        return ApiResponse.success("Cart item removed successfully");
    }
}
