package com.smartlearnly.backend.commerce.dto;

import java.util.List;

public record TransactionFilterOptionsResponse(
        List<String> statuses,
        List<String> paymentGateways,
        List<String> currencies
) {
}