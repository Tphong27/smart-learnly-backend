package com.smartlearnly.backend.payment.sepay.service;

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
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.enrollment.service.ClassEnrollmentService;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.payment.sepay.dto.SePayWebhookPayload;
import com.smartlearnly.backend.payment.sepay.repository.SePayInvoiceNumberRepository;
import com.smartlearnly.backend.payment.sepay.repository.SePayWebhookEventRepository;
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

/**
 * Unit tests for {@link SePayPaymentMatchingService} covering the most complex
 * functions: transaction date normalization ({@code resolvePaidAt}), payment
 * code extraction, mismatch/failure branch recording and account number
 * normalization. Pure Mockito/JUnit tests with a fixed clock, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class SePayPaymentMatchingServiceUnitTest {

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
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;

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
                auditLogService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service.setNotificationService(notificationService);
    }

    @Test
    void processShouldParseIsoInstantTransactionDate() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000001");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), "2026-06-19T17:30:00Z"));

        assertThat(transaction.getPaidAt()).isEqualTo(Instant.parse("2026-06-19T17:30:00Z"));
    }

    @Test
    void processShouldParseOffsetDateTimeTransactionDate() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000002");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        // Short offset "+07" is rejected by Instant.parse, so this input is the
        // one that actually reaches the OffsetDateTime.parse branch of resolvePaidAt.
        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), "2026-06-19T17:30:00+07"));

        assertThat(transaction.getPaidAt()).isEqualTo(Instant.parse("2026-06-19T10:30:00Z"));
    }

    @Test
    void processShouldParseVietnamLocalTransactionDate() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000003");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), "2026-06-19 17:30:00"));

        assertThat(transaction.getPaidAt()).isEqualTo(Instant.parse("2026-06-19T10:30:00Z"));
    }

    @Test
    void processShouldFallbackToClockWhenTransactionDateIsInvalid() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000004");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), "not-a-date"));

        assertThat(transaction.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    void processShouldMarkMismatchedWhenContentHasMultiplePaymentCodes() {
        service.process(payload(null, "code " + PAYMENT_CODE + " and SLPZZZ999YYY000",
                "in", null, new BigDecimal("399000"), null));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay payment code was not found");
    }

    @Test
    void processShouldMarkMismatchedWhenPaymentCodeMatchesNoOrder() {
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.empty());

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), null));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay payment code did not match an order");
    }

    @Test
    void processShouldMarkFailedWhenLocalRecordsMissing() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE))
                .thenReturn(Optional.of(sePayOrder(orderId, transactionId)));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.empty());

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), null));

        verify(webhookEventRepository).markFailed(EVENT_ID, "Matched payment references missing local records");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processShouldMarkMismatchedWhenSePayOrderIsExpired() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        sePayOrder.setStatus(SePayOrderStatus.EXPIRED);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId))
                .thenReturn(Optional.of(transaction(transactionId, orderId, UUID.randomUUID())));
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(orderId, UUID.randomUUID())));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), null));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay order is not waiting for payment");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processShouldMarkMismatchedWhenTransferAmountIsMissing() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE))
                .thenReturn(Optional.of(sePayOrder(orderId, transactionId)));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId))
                .thenReturn(Optional.of(transaction(transactionId, orderId, UUID.randomUUID())));
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(orderId, UUID.randomUUID())));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789", null, null));

        verify(webhookEventRepository).markMismatched(EVENT_ID, "SePay payment amount did not match");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processShouldMatchReceivingAccountIgnoringWhitespace() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        sePayOrder.setBankAccountNumber("123 456 789");
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(invoiceNumberRepository.nextInvoiceNumber()).thenReturn("SLP-INV-0000000005");
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), null));

        verify(webhookEventRepository).markProcessed(EVENT_ID);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void processShouldKeepExistingInvoiceNumber() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SePayOrder sePayOrder = sePayOrder(orderId, transactionId);
        PaymentTransaction transaction = transaction(transactionId, orderId, studentId);
        transaction.setInvoiceNumber("INV-EXISTING");
        PurchaseOrder order = order(orderId, studentId);
        when(sePayOrderRepository.findByPaymentCodeForUpdate(PAYMENT_CODE)).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findByIdForUpdate(transactionId)).thenReturn(Optional.of(transaction));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(orderItem(orderId, courseId)));

        service.process(payload(PAYMENT_CODE, null, "in", "123456789",
                new BigDecimal("399000"), null));

        assertThat(transaction.getInvoiceNumber()).isEqualTo("INV-EXISTING");
        verify(invoiceNumberRepository, never()).nextInvoiceNumber();
        verify(webhookEventRepository).markProcessed(EVENT_ID);
    }

    private SePayWebhookPayload payload(String code, String content, String transferType,
            String accountNumber, BigDecimal transferAmount, String transactionDate) {
        return new SePayWebhookPayload(
                EVENT_ID, code, content, transferType, transferAmount, accountNumber, transactionDate, "FT24012345678");
    }

    private SePayOrder sePayOrder(UUID orderId, UUID transactionId) {
        SePayOrder sePayOrder = new SePayOrder();
        sePayOrder.setId(UUID.randomUUID());
        sePayOrder.setOrderId(orderId);
        sePayOrder.setTransactionId(transactionId);
        sePayOrder.setPaymentCode(PAYMENT_CODE);
        sePayOrder.setTransferContent("SEVQR " + PAYMENT_CODE);
        sePayOrder.setBankAccountNumber("123456789");
        sePayOrder.setBankName("MBBANK");
        sePayOrder.setAccountName("SMART LEARNLY");
        sePayOrder.setAmount(new BigDecimal("399000"));
        sePayOrder.setStatus(SePayOrderStatus.WAITING_PAYMENT);
        return sePayOrder;
    }

    private PaymentTransaction transaction(UUID transactionId, UUID orderId, UUID studentId) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setUserId(studentId);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAmount(new BigDecimal("399000"));
        transaction.setCurrency("VND");
        return transaction;
    }

    private PurchaseOrder order(UUID orderId, UUID studentId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(orderId);
        order.setUserId(studentId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("399000"));
        order.setCurrency("VND");
        return order;
    }

    private OrderItem orderItem(UUID orderId, UUID courseId) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrderId(orderId);
        item.setCourseId(courseId);
        item.setFinalAmount(new BigDecimal("399000"));
        return item;
    }
}
