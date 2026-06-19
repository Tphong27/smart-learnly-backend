package com.smartlearnly.backend.payment.sepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrderStatus;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SePayReconciliationServiceTest {
    @Mock
    private SePayOrderRepository sePayOrderRepository;
    @Mock
    private SePayTransactionClient sePayTransactionClient;
    @Mock
    private SePayPaymentMatchingService paymentMatchingService;

    private SePayProperties sePayProperties;
    private SePayReconciliationService service;

    @BeforeEach
    void setUp() {
        sePayProperties = new SePayProperties();
        service = new SePayReconciliationService(
                sePayProperties,
                sePayOrderRepository,
                sePayTransactionClient,
                paymentMatchingService
        );
    }

    @Test
    void reconcileShouldSkipWhenApiTokenIsBlank() {
        service.reconcile();

        verifyNoInteractions(sePayOrderRepository, sePayTransactionClient, paymentMatchingService);
    }

    @Test
    void reconcileShouldQueryPendingSePayOrdersAndPassCandidatesToMatchingFlow() {
        sePayProperties.setApiToken("fake-api-token");
        SePayOrder sePayOrder = sePayOrder("SLPABC123DEF456", new BigDecimal("399000"));
        SePayTransactionCandidate candidate = candidate("SLPABC123DEF456", new BigDecimal("399000"));
        when(sePayOrderRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(sePayOrder));
        when(sePayTransactionClient.findTransactions(any())).thenReturn(List.of(candidate));

        service.reconcile();

        ArgumentCaptor<SePayTransactionQuery> queryCaptor = ArgumentCaptor.forClass(SePayTransactionQuery.class);
        verify(sePayTransactionClient).findTransactions(queryCaptor.capture());
        assertThat(queryCaptor.getValue().q()).isEqualTo("SLPABC123DEF456");
        assertThat(queryCaptor.getValue().transferType()).isEqualTo("in");
        assertThat(queryCaptor.getValue().amountInMin()).isEqualByComparingTo("399000");
        assertThat(queryCaptor.getValue().amountInMax()).isEqualByComparingTo("399000");
        assertThat(queryCaptor.getValue().perPage()).isEqualTo(20);
        assertThat(queryCaptor.getValue().timestampFormat()).isEqualTo("iso8601");
        verify(paymentMatchingService).processReconciledTransaction(candidate);
    }

    @Test
    void reconcileShouldNotInvokeMatchingWhenApiFailureOccurs() {
        sePayProperties.setApiToken("fake-api-token");
        SePayOrder sePayOrder = sePayOrder("SLPABC123DEF456", new BigDecimal("399000"));
        when(sePayOrderRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(sePayOrder));
        when(sePayTransactionClient.findTransactions(any())).thenThrow(new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "SePay transaction service is unavailable"
        ));

        service.reconcile();

        verify(paymentMatchingService, never()).processReconciledTransaction(any());
    }

    private SePayOrder sePayOrder(String paymentCode, BigDecimal amount) {
        SePayOrder sePayOrder = new SePayOrder();
        sePayOrder.setId(UUID.randomUUID());
        sePayOrder.setOrderId(UUID.randomUUID());
        sePayOrder.setTransactionId(UUID.randomUUID());
        sePayOrder.setPaymentCode(paymentCode);
        sePayOrder.setBankAccountNumber("123456789");
        sePayOrder.setBankName("MBBANK");
        sePayOrder.setAccountName("SMART LEARNLY");
        sePayOrder.setAmount(amount);
        sePayOrder.setQrUrl("https://qr.example/pay");
        sePayOrder.setStatus(SePayOrderStatus.WAITING_PAYMENT);
        return sePayOrder;
    }

    private SePayTransactionCandidate candidate(String paymentCode, BigDecimal amount) {
        return new SePayTransactionCandidate(
                "0f171a36-5a4e-4e00-b7fb-c8a4560d9c10",
                "2026-06-19T17:30:00+07:00",
                "123456789",
                "in",
                amount,
                "Thanh toan " + paymentCode,
                "FT24012345678",
                paymentCode
        );
    }
}
