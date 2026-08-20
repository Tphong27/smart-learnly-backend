package com.smartlearnly.backend.commerce.transaction.service;

import com.smartlearnly.backend.commerce.transaction.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionFilterOptionsResponse;
import com.smartlearnly.backend.commerce.entity.PaymentGateway;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    // Tìm giao dịch thuộc học viên hiện tại theo phân trang và các bộ lọc tùy chọn.
    public PageResponse<TransactionResponse> listMyTransactions(
            int page,
            int size,
            String keyword,
            TransactionStatus status,
            PaymentGateway paymentGateway,
            String currency) {
        UUID userId = currentUserService.requireAuthenticatedUser().getId();

        Page<PaymentTransaction> transactions = paymentTransactionRepository.searchByUserId(
                userId,
                normalizeKeyword(keyword),
                status == null ? null : status.name(),
                paymentGateway == null ? null : paymentGateway.name(),
                normalizeCurrency(currency),
                createPageRequest(page, size));

        return toPageResponse(transactions);
    }

    @Transactional(readOnly = true)
    // Tìm toàn bộ giao dịch cho TMO (ops thanh toán) và từ chối các vai trò khác.
    public PageResponse<TransactionResponse> listAllTransactions(
            int page,
            int size,
            String keyword,
            TransactionStatus status,
            PaymentGateway paymentGateway,
            String currency) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        if (!isAdminOrTmo(actor)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Only TMO can view all transactions");
        }

        Page<PaymentTransaction> transactions = paymentTransactionRepository.searchAll(
                normalizeKeyword(keyword),
                status == null ? null : status.name(),
                paymentGateway == null ? null : paymentGateway.name(),
                normalizeCurrency(currency),
                createPageRequest(page, size));

        return toPageResponse(transactions);
    }

    @Transactional(readOnly = true)
    // Lấy một giao dịch và kiểm tra người gọi là chủ sở hữu hoặc TMO.
    public TransactionResponse getTransaction(UUID transactionId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Transaction was not found"));

        if (!transaction.getUserId().equals(actor.getId()) && !isAdminOrTmo(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Transaction access is denied");
        }

        return toTransactionResponse(transaction);
    }

    @Transactional(readOnly = true)
    // Tạo dữ liệu hóa đơn cho giao dịch thành công mà người gọi được phép xem.
    public InvoiceResponse getInvoice(UUID transactionId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Transaction was not found"));

        if (!transaction.getUserId().equals(actor.getId()) && !isAdminOrTmo(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invoice access is denied");
        }

        if (transaction.getStatus() != TransactionStatus.SUCCESS || transaction.getInvoiceNumber() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Invoice is not available yet");
        }

        UserAccount trainee = userRepository.findById(transaction.getUserId())
                .orElse(null);

        return new InvoiceResponse(
                transaction.getId(),
                transaction.getOrderId(),
                transaction.getInvoiceNumber(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                transaction.getPaidAt(),
                trainee == null ? null : trainee.getFullName(),
                trainee == null ? null : trainee.getEmail(),
                trainee == null ? null : trainee.getPhoneNumber());
    }

    @Transactional(readOnly = true)
    // Lấy các giá trị bộ lọc giao dịch cho Trainee và TMO đã đăng nhập.
    public TransactionFilterOptionsResponse getFilterOptions() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        if (!isTransactionViewer(actor)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Only Trainee or TMO can view transaction filter options");
        }

        return new TransactionFilterOptionsResponse(
                paymentTransactionRepository.findDistinctStatuses(),
                paymentTransactionRepository.findDistinctPaymentGateways(),
                paymentTransactionRepository.findDistinctCurrencies());
    }

    // Chuẩn hóa mã tiền tệ về chữ hoa hoặc trả null khi không lọc.
    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }

        return currency.trim().toUpperCase(Locale.ROOT);
    }

    // Chuyển trang entity thành JSON phân trang ổn định của API.
    private PageResponse<TransactionResponse> toPageResponse(Page<PaymentTransaction> transactions) {
        return new PageResponse<>(
                transactions.stream().map(this::toTransactionResponse).toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages());
    }

    // Chuyển entity giao dịch thành DTO không làm lộ dữ liệu nội bộ.
    private TransactionResponse toTransactionResponse(PaymentTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getOrderId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                transaction.getPaymentGateway() == null ? null : transaction.getPaymentGateway().name(),
                transaction.getInvoiceNumber(),
                transaction.getPaidAt(),
                transaction.getExpiresAt(),
                transaction.getCreatedAt());
    }

    // Tạo yêu cầu phân trang và giới hạn kích thước tối đa 100 bản ghi.
    private PageRequest createPageRequest(int page, int size) {
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    }

    // Loại khoảng trắng ngoài từ khóa hoặc trả null khi không tìm kiếm.
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    // Kiểm tra vai trò được phép truy vấn giao dịch của mọi người dùng (ops thanh toán: TMO).
    private boolean isAdminOrTmo(UserAccount user) {
        return "TMO".equalsIgnoreCase(user.getRole());
    }

    // Kiểm tra vai trò được phép sử dụng các giá trị lọc giao dịch.
    private boolean isTransactionViewer(UserAccount user) {
        return "TRAINEE".equalsIgnoreCase(user.getRole())
                || isAdminOrTmo(user);
    }
}
