package com.smartlearnly.backend.commerce.transaction.dto;

import java.util.List;

public record TransactionFilterOptionsResponse(
        List<String> statuses,
        List<String> paymentGateways,
        List<String> currencies
) {
}
