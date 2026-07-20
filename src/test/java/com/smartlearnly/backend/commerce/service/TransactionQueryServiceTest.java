package com.smartlearnly.backend.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.commerce.dto.TransactionResponse;
import com.smartlearnly.backend.commerce.entity.PaymentGateway;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    private TransactionQueryService transactionQueryService;

    private UserAccount owner;
    private UserAccount otherTrainee;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        transactionQueryService = new TransactionQueryService(
                paymentTransactionRepository,
                currentUserService,
                userRepository
        );

        owner = new UserAccount();
        owner.setId(UUID.randomUUID());
        owner.setRole("TRAINEE");

        otherTrainee = new UserAccount();
        otherTrainee.setId(UUID.randomUUID());
        otherTrainee.setRole("TRAINEE");

        admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");
    }

    @Test
    void getTransactionShouldReturnOwnedTransaction() {
        PaymentTransaction transaction = sampleTransaction(owner.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(owner);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        TransactionResponse response = transactionQueryService.getTransaction(transaction.getId());

        assertThat(response.id()).isEqualTo(transaction.getId());
        assertThat(response.orderId()).isEqualTo(transaction.getOrderId());
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS.name());
        assertThat(response.paymentGateway()).isEqualTo(PaymentGateway.SEPAY.name());
    }

    @Test
    void getTransactionShouldAllowAdmin() {
        PaymentTransaction transaction = sampleTransaction(owner.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        TransactionResponse response = transactionQueryService.getTransaction(transaction.getId());

        assertThat(response.id()).isEqualTo(transaction.getId());
    }

    @Test
    void getTransactionShouldRejectOtherTrainee() {
        PaymentTransaction transaction = sampleTransaction(owner.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(otherTrainee);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> transactionQueryService.getTransaction(transaction.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private PaymentTransaction sampleTransaction(UUID userId) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setOrderId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("250000"));
        transaction.setCurrency("VND");
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setPaymentGateway(PaymentGateway.SEPAY);
        transaction.setInvoiceNumber("INV-001");
        transaction.setPaidAt(Instant.now());
        transaction.setCreatedAt(Instant.now());
        return transaction;
    }
}
