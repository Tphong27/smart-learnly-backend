package com.smartlearnly.backend.commerce.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.commerce.entity.PaymentGateway;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.commerce.transaction.dto.InvoiceResponse;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link TransactionQueryService} covering the most complex
 * functions: pagination/filter normalization, admin/TMO authorization and the
 * invoice resolution flow. Pure Mockito/JUnit tests, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceUnitTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserRepository userRepository;

    private TransactionQueryService service;

    private UserAccount trainee;
    private UserAccount otherTrainee;
    private UserAccount tmo;

    @BeforeEach
    void setUp() {
        service = new TransactionQueryService(paymentTransactionRepository, currentUserService, userRepository);

        trainee = user("TRAINEE");
        otherTrainee = user("TRAINEE");
        tmo = user("TMO");
    }

    @Test
    void listMyTransactionsShouldNormalizeFiltersAndCapPageSize() {
        UUID userId = trainee.getId();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(paymentTransactionRepository.searchByUserId(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleTransaction(userId))));

        service.listMyTransactions(0, 500, "  INV-1  ", TransactionStatus.SUCCESS,
                PaymentGateway.SEPAY, " vnd ");

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> currencyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentTransactionRepository).searchByUserId(
                eq(userId),
                keywordCaptor.capture(),
                eq("SUCCESS"),
                eq("SEPAY"),
                currencyCaptor.capture(),
                pageableCaptor.capture());

        assertThat(keywordCaptor.getValue()).isEqualTo("INV-1");
        assertThat(currencyCaptor.getValue()).isEqualTo("VND");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listAllTransactionsShouldAllowTmo() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(paymentTransactionRepository.searchAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleTransaction(trainee.getId()))));

        PageResponse<TransactionResponse> response =
                service.listAllTransactions(0, 20, null, null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalItems()).isEqualTo(1);
    }

    @Test
    void listAllTransactionsShouldRejectTrainee() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);

        assertThatThrownBy(() -> service.listAllTransactions(0, 20, null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void getTransactionShouldRejectUnknownId() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(paymentTransactionRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getInvoiceShouldReturnTraineeDetailsForSuccessfulTransaction() {
        PaymentTransaction transaction = sampleTransaction(trainee.getId());
        transaction.setInvoiceNumber("INV-100");
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setPaidAt(Instant.parse("2026-07-01T08:00:00Z"));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(userRepository.findById(trainee.getId())).thenReturn(Optional.of(trainee));

        InvoiceResponse response = service.getInvoice(transaction.getId());

        assertThat(response.invoiceNumber()).isEqualTo("INV-100");
        assertThat(response.traineeName()).isEqualTo("Trainee Name");
        assertThat(response.traineeEmail()).isEqualTo("trainee@example.com");
        assertThat(response.traineePhoneNumber()).isEqualTo("0900000000");
    }

    @Test
    void getInvoiceShouldReturnNullTraineeWhenAccountMissing() {
        PaymentTransaction transaction = sampleTransaction(trainee.getId());
        transaction.setInvoiceNumber("INV-100");
        transaction.setStatus(TransactionStatus.SUCCESS);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(userRepository.findById(trainee.getId())).thenReturn(Optional.empty());

        InvoiceResponse response = service.getInvoice(transaction.getId());

        assertThat(response.traineeName()).isNull();
        assertThat(response.traineeEmail()).isNull();
        assertThat(response.traineePhoneNumber()).isNull();
    }

    @Test
    void getInvoiceShouldRejectWhenTransactionIsNotPaid() {
        PaymentTransaction transaction = sampleTransaction(trainee.getId());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setInvoiceNumber("INV-100");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.getInvoice(transaction.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getInvoiceShouldRejectOtherTrainee() {
        PaymentTransaction transaction = sampleTransaction(trainee.getId());
        transaction.setInvoiceNumber("INV-100");
        transaction.setStatus(TransactionStatus.SUCCESS);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(otherTrainee);
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.getInvoice(transaction.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setFullName("Trainee Name");
        user.setEmail("trainee@example.com");
        user.setPhoneNumber("0900000000");
        return user;
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
