package com.smartlearnly.backend.commerce.controller;

import com.smartlearnly.backend.commerce.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.service.TransactionQueryService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        PageResponse<TransactionResponse> result = isAdminOrTmo(actor)
                ? transactionQueryService.listAllTransactions(page, size)
                : transactionQueryService.listMyTransactions(page, size);

        return ApiResponse.success("Transactions loaded successfully", result);
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