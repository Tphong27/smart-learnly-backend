package com.smartlearnly.backend.commerce.service;

import com.smartlearnly.backend.commerce.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
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
    public PageResponse<TransactionResponse> listMyTransactions(int page, int size) {
        UUID userId = currentUserService.requireAuthenticatedUser().getId();
        Page<PaymentTransaction> transactions = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));

        return toPageResponse(transactions);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> listAllTransactions(int page, int size) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (!isAdminOrTmo(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only Admin or TMO can view all transactions");
        }
        Page<PaymentTransaction> transactions = paymentTransactionRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));

        return toPageResponse(transactions);
    }

    @Transactional(readOnly = true)
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

    private PageResponse<TransactionResponse> toPageResponse(Page<PaymentTransaction> transactions) {
        return new PageResponse<>(
                transactions.stream().map(this::toTransactionResponse).toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages());
    }

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

    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }
}