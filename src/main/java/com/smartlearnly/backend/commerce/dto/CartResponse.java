package com.smartlearnly.backend.commerce.dto;

import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID cartId,
        List<CartItemResponse> items
) {
}
