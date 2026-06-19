package com.smartlearnly.backend.payment.sepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.commerce.entity.OrderItem;
import com.smartlearnly.backend.commerce.entity.OrderStatus;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.PurchaseOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrderStatus;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.OrderItemRepository;
import com.smartlearnly.backend.commerce.repository.OrderRepository;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.enrollment.service.ClassEnrollmentService;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SePayPaymentMatchingServiceTest {
    private static final String PAYMENT_CODE = "SLPABC123DEF456";
    private static final long EVENT_ID = 92704L;
    private static final Instant NOW = Instant.parse("2026-06-19T10:30:00Z");

    @Mock
    private SePayOrderRepository sePayOrderRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private ClassEnrollmentService classEnrollmentService;
    @Mock
    private SePayWebhookEventRepository webhookEventRepository;
    @Mock
    private SePayInvoiceNumberRepository invoiceNumberRepository;

    private SePayProperties sePayProperties;
    private SePayPaymentMatchingService service;

    @BeforeEach
    void setUp() {
        sePayProperties = new SePayProperties();
        sePayProperties.setPaymentCodePrefix("SLP");
        service = new SePayPaymentMatchingService(
                sePayProperties,
                sePayOrderRepository,
                paymentTransactionRepository,
                orderRepository,
                orderItemRepository,
                courseEnrollmentService,
                classEnrollmentService,
                webhookEventRepository,
                invoiceNumberRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void processShouldApplySuccessfulCourseOnlyPayment() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId, TransactionStatus.PENDING);
        PurchaseOrder order = order(orderId, studentId, OrderStatus.PENDING);
        OrderItem item = orderItem(orderId, courseId, null, new BigDecimal("399000"));
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000042");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(item));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789", new BigDecimal("399000")));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getGatewayEventId()).isEqualTo(EVENT_ID);
        assertThat(transaction.getGatewayTransactionId()).isEqualTo("FT24012345678");
        assertThat(transaction.getInvoiceNumber()).isEqualTo("SLP-INV-0000000042");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(sePayOrder.getStatus()).isEqualTo(SePayOrderStatus.MATCHED);
        verify(courseEnrollmentService).grantPaidCourseEnrollment(studentId, courseId, transactionId);
        verify(classEnrollmentService, never()).grantPaidClassEnrollment(any(), any(), any(), any());
        verify(webhookEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void processShouldApplySuccessfulClassPaymentUsingCodeExtractedFromContent() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId, TransactionStatus.PENDING);
        PurchaseOrder order = order(orderId, studentId, OrderStatus.PENDING);
        OrderItem item = orderItem(orderId, UUID.randomUUID(), classId, new BigDecimal("500000"));
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000043");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(item));

        service.process(payload(null, "Thanh toan " + PAYMENT_CODE, "in", null, new BigDecimal("399000")));

        verify(classEnrollmentService).grantPaidClassEnrollment(
                studentId,
                classId,
                new BigDecimal("500000"),
                transactionId
        );
        verify(courseEnrollmentService, never()).grantPaidCourseEnrollment(any(), any(), any());
        verify(webhookEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void processShouldMarkMismatchedWhenAmountDoesNotMatch() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId))
                .thenReturn(Optional.of(transaction(transactionId, orderId, UUID.randomUUID(), TransactionStatus.PENDING)));
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(orderId, UUID.randomUUID(), OrderStatus.PENDING)));

        service.process(payload(PAYMENT_CODE, null, "in", null, new BigDecimal("398999")));

        assertThat(sePayOrder.getStatus()).isEqualTo(SePayOrderStatus.WAITING_PAYMENT);
        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay payment amount did not match");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processShouldMarkMismatchedWhenPaymentCodeIsMissingOrNotFound() {
        service.process(payload(null, "No matching payment content", "in", null, new BigDecimal("399000")));
        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay payment code was not found");
        verify(sePayOrderRepository, never()).findByPaymentCodeForUpdate(any());
    }

    @Test
    void processShouldMarkMismatchedWhenTransferIsNotInbound() {
        service.process(payload(PAYMENT_CODE, null, "out", null, new BigDecimal("399000")));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay transfer is not inbound");
        verify(sePayOrderRepository, never()).findByPaymentCodeForUpdate(any());
    }

    @Test
    void processShouldMarkMismatchedWhenReceivingBankAccountDoesNotMatch() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId))
                .thenReturn(Optional.of(transaction(transactionId, orderId, UUID.randomUUID(), TransactionStatus.PENDING)));
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(orderId, UUID.randomUUID(), OrderStatus.PENDING)));

        service.process(payload(PAYMENT_CODE, null, "in", "987654321", new BigDecimal("399000")));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay receiving account did not match");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processShouldHandleAlreadyPaidTransactionIdempotently() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        sePayOrder.setStatus(SePayOrderStatus.MATCHED);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId, TransactionStatus.SUCCESS);
        PurchaseOrder order = order(orderId, studentId, OrderStatus.PAID);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.process(payload(PAYMENT_CODE, null, "in", null, new BigDecimal("399000")));

        verify(webhookEventRepository).markProcessed(EVENT_ID);
        verify(courseEnrollmentService, never()).grantPaidCourseEnrollment(any(), any(), any());
        verify(classEnrollmentService, never()).grantPaidClassEnrollment(any(), any(), any(), any());
        verify(invoiceNumberRepository, never()).nextInvoiceNumber();
    }

    private SePayWebhookPayload payload(
            String code,
            String content,
            String transferType,
            String accountNumber,
            BigDecimal transferAmount
    ) {
        return new SePayWebhookPayload(
                EVENT_ID,
                code,
                content,
                transferType,
                transferAmount,
                accountNumber,
                null,
                "FT24012345678"
        );
    }

    private SePayOrder sePayOrder(UUID orderId, UUID transactionId) {
        SePayOrder sePayOrder = new SePayOrder();
        sePayOrder.setId(UUID.randomUUID());
        sePayOrder.setOrderId(orderId);
        sePayOrder.setTransactionId(transactionId);
        sePayOrder.setPaymentCode(PAYMENT_CODE);
        sePayOrder.setBankAccountNumber("123456789");
        sePayOrder.setBankName("MBBANK");
        sePayOrder.setAccountName("SMART LEARNLY");
        sePayOrder.setAmount(new BigDecimal("399000"));
        sePayOrder.setStatus(SePayOrderStatus.WAITING_PAYMENT);
        return sePayOrder;
    }

    private PaymentTransaction transaction(
            UUID transactionId,
            UUID orderId,
            UUID studentId,
            TransactionStatus status
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setUserId(studentId);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal("399000"));
        transaction.setCurrency("VND");
        return transaction;
    }

    private PurchaseOrder order(UUID orderId, UUID studentId, OrderStatus status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(orderId);
        order.setUserId(studentId);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("399000"));
        order.setCurrency("VND");
        return order;
    }

    private OrderItem orderItem(UUID orderId, UUID courseId, UUID classId, BigDecimal finalAmount) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrderId(orderId);
        item.setCourseId(courseId);
        item.setClassId(classId);
        item.setFinalAmount(finalAmount);
        return item;
    }
}
