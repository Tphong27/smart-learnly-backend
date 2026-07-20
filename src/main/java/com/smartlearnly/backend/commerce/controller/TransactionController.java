package com.smartlearnly.backend.commerce.controller;

import com.smartlearnly.backend.commerce.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.service.TransactionQueryService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Transaction query APIs")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final TransactionQueryService transactionQueryService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "List transactions. Admin/TMO see all; Trainee sees own transactions")
    public ApiResponse<PageResponse<TransactionResponse>> listTransactions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) TransactionStatus status) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        PageResponse<TransactionResponse> result = isAdminOrTmo(actor)
                ? transactionQueryService.listAllTransactions(page, size, keyword, status)
                : transactionQueryService.listMyTransactions(page, size, keyword, status);

        return ApiResponse.success("Transactions loaded successfully", result);
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get transaction detail")
    public ApiResponse<TransactionResponse> getTransaction(@PathVariable UUID transactionId) {
        return ApiResponse.success(
                "Transaction loaded successfully",
                transactionQueryService.getTransaction(transactionId));
    }

    @GetMapping("/{transactionId}/invoice")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get transaction invoice")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID transactionId) {
        return ApiResponse.success(
                "Invoice loaded successfully",
                transactionQueryService.getInvoice(transactionId));
    }

    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }
}
