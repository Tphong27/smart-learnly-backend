package com.smartlearnly.backend.commerce.controller;

import com.smartlearnly.backend.commerce.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.service.TransactionQueryService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "User transaction and invoice metadata APIs")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final TransactionQueryService transactionQueryService;

    @GetMapping
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "List authenticated trainee transactions")
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(transactionQueryService.listMyTransactions(page, size));
    }

    @GetMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get invoice metadata for a successful transaction")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return ApiResponse.success("Invoice loaded successfully", transactionQueryService.getInvoice(id));
    }
}
