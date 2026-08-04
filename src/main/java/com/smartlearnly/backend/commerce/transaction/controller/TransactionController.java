package com.smartlearnly.backend.commerce.transaction.controller;

import com.smartlearnly.backend.commerce.transaction.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.transaction.service.TransactionQueryService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionFilterOptionsResponse;
import com.smartlearnly.backend.commerce.entity.PaymentGateway;
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
    // Trả giao dịch của học viên hiện tại hoặc toàn bộ giao dịch khi người gọi là Admin/TMO.
    public ApiResponse<PageResponse<TransactionResponse>> listTransactions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) PaymentGateway paymentGateway,
            @RequestParam(required = false) @Size(max = 10) String currency) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        PageResponse<TransactionResponse> result = isAdminOrTmo(actor)
                ? transactionQueryService.listAllTransactions(
                        page,
                        size,
                        keyword,
                        status,
                        paymentGateway,
                        currency)
                : transactionQueryService.listMyTransactions(
                        page,
                        size,
                        keyword,
                        status,
                        paymentGateway,
                        currency);
        return ApiResponse.success("Transactions loaded successfully", result);
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Get transaction filter options for admin monitoring")
    // Trả các giá trị lọc đang có trong dữ liệu để màn hình admin tạo bộ lọc.
    public ApiResponse<TransactionFilterOptionsResponse> getFilterOptions() {
        return ApiResponse.success(
                "Transaction filter options loaded successfully",
                transactionQueryService.getFilterOptions());
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get transaction detail")
    // Trả chi tiết giao dịch sau khi service kiểm tra quyền sở hữu hoặc quyền quản trị.
    public ApiResponse<TransactionResponse> getTransaction(@PathVariable UUID transactionId) {
        return ApiResponse.success(
                "Transaction loaded successfully",
                transactionQueryService.getTransaction(transactionId));
    }

    @GetMapping("/{transactionId}/invoice")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "Get transaction invoice")
    // Trả dữ liệu hóa đơn của giao dịch đã thanh toán thành công.
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID transactionId) {
        return ApiResponse.success(
                "Invoice loaded successfully",
                transactionQueryService.getInvoice(transactionId));
    }

    // Kiểm tra vai trò được phép xem dữ liệu giao dịch toàn hệ thống.
    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }
}
