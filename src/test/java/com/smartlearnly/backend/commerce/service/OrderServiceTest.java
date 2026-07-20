package com.smartlearnly.backend.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.OrderResponse;
import com.smartlearnly.backend.commerce.dto.OrderSummaryResponse;
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
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private SePayOrderRepository sePayOrderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    private OrderService orderService;

    private UserAccount admin;
    private UserAccount trainee;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                paymentTransactionRepository,
                sePayOrderRepository,
                currentUserService,
                auditLogService,
                classOfferingRepository
        );

        admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");

        trainee = new UserAccount();
        trainee.setId(UUID.randomUUID());
        trainee.setRole("TRAINEE");
    }

    @Test
    void listOrdersShouldReturnSummariesForAdmin() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);

        PurchaseOrder order = pendingOrder(trainee.getId(), Instant.now().plus(10, ChronoUnit.MINUTES));
        when(orderRepository.searchAll(eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<OrderSummaryResponse> page = orderService.listOrders(0, 20, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).orderCode()).isEqualTo(order.getOrderCode());
        assertThat(page.items().get(0).amount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(page.items().get(0).status()).isEqualTo(OrderStatus.PENDING.name());
    }

    @Test
    void listOrdersShouldRejectTrainee() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);

        assertThatThrownBy(() -> orderService.listOrders(0, 20, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getOrderShouldLazyExpireDuePendingOrder() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);

        PurchaseOrder order = pendingOrder(trainee.getId(), Instant.now().minus(1, ChronoUnit.MINUTES));
        PaymentTransaction transaction = pendingTransaction(order);
        SePayOrder sePayOrder = waitingSePayOrder(transaction);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of());
        when(paymentTransactionRepository.findByOrderIdAndStatus(order.getId(), TransactionStatus.PENDING))
                .thenReturn(List.of(transaction));
        when(sePayOrderRepository.findByTransactionId(transaction.getId())).thenReturn(Optional.of(sePayOrder));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(Optional.of(transaction));

        OrderResponse response = orderService.getOrder(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.EXPIRED.name());
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(sePayOrder.getStatus()).isEqualTo(SePayOrderStatus.EXPIRED);
        verify(orderRepository).save(order);
        verify(auditLogService).recordSystem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getOrderShouldNotExpireActivePendingOrder() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);

        PurchaseOrder order = pendingOrder(trainee.getId(), Instant.now().plus(20, ChronoUnit.MINUTES));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(Optional.empty());

        OrderResponse response = orderService.getOrder(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING.name());
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void expireDueOrdersShouldProcessDueBatch() {
        PurchaseOrder order = pendingOrder(trainee.getId(), Instant.now().minus(5, ChronoUnit.MINUTES));
        PaymentTransaction transaction = pendingTransaction(order);
        SePayOrder sePayOrder = waitingSePayOrder(transaction);

        when(orderRepository.findDuePendingOrderIds(any(Instant.class), anyInt()))
                .thenReturn(List.of(order.getId()));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(paymentTransactionRepository.findByOrderIdAndStatus(order.getId(), TransactionStatus.PENDING))
                .thenReturn(List.of(transaction));
        when(sePayOrderRepository.findByTransactionId(transaction.getId())).thenReturn(Optional.of(sePayOrder));

        int expired = orderService.expireDueOrders(50);

        assertThat(expired).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(sePayOrder.getStatus()).isEqualTo(SePayOrderStatus.EXPIRED);

        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    private PurchaseOrder pendingOrder(UUID userId, Instant expiresAt) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setOrderCode("ORD-TEST-001");
        order.setTotalAmount(new BigDecimal("199000"));
        order.setCurrency("VND");
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(expiresAt);
        order.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        order.setUpdatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        return order;
    }

    private PaymentTransaction pendingTransaction(PurchaseOrder order) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setOrderId(order.getId());
        transaction.setUserId(order.getUserId());
        transaction.setAmount(order.getTotalAmount());
        transaction.setCurrency(order.getCurrency());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setExpiresAt(order.getExpiresAt());
        transaction.setCreatedAt(order.getCreatedAt());
        return transaction;
    }

    private SePayOrder waitingSePayOrder(PaymentTransaction transaction) {
        SePayOrder sePayOrder = new SePayOrder();
        sePayOrder.setId(UUID.randomUUID());
        sePayOrder.setOrderId(transaction.getOrderId());
        sePayOrder.setTransactionId(transaction.getId());
        sePayOrder.setPaymentCode("SLPTESTCODE01");
        sePayOrder.setBankAccountNumber("0123456789");
        sePayOrder.setBankName("VCB");
        sePayOrder.setAccountName("SMART LEARNLY");
        sePayOrder.setAmount(transaction.getAmount());
        sePayOrder.setQrUrl("https://example.com/qr");
        sePayOrder.setStatus(SePayOrderStatus.WAITING_PAYMENT);
        sePayOrder.setExpiresAt(transaction.getExpiresAt());
        return sePayOrder;
    }
}
