package com.smartlearnly.backend.commerce.service;

import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.OrderItemResponse;
import com.smartlearnly.backend.commerce.dto.OrderResponse;
import com.smartlearnly.backend.commerce.dto.OrderSummaryResponse;
import com.smartlearnly.backend.commerce.dto.SePayOrderSummaryResponse;
import com.smartlearnly.backend.commerce.dto.TransactionResponse;
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
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_EXPIRE_BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SePayOrderRepository sePayOrderRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ClassOfferingRepository classOfferingRepository;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(int page, int size, String keyword, OrderStatus status) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (!isAdminOrTmo(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only Admin or TMO can view all orders");
        }

        Page<PurchaseOrder> orders = orderRepository.searchAll(
                normalizeKeyword(keyword),
                status == null ? null : status.name(),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));

        return new PageResponse<>(
                orders.stream().map(this::toOrderSummaryResponse).toList(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages());
    }

    @Transactional
    public OrderResponse getOrder(UUID orderId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order was not found"));
        requireOwnerOrAdmin(actor, order);
        order = expireIfDue(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order was not found"));
        if (!order.getUserId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the order owner can cancel this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only pending orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        PurchaseOrder saved = orderRepository.save(order);

        closePendingPaymentSession(saved);

        auditLogService.recordUser(
                actor, AuditAction.ORDER_CANCELLED, AuditDomain.ORDER, AuditResult.SUCCESS,
                "ORDER", saved.getId().toString(), "Order was cancelled",
                Map.of("status", OrderStatus.PENDING.name()),
                Map.of("status", OrderStatus.CANCELLED.name()),
                Map.of("orderCode", saved.getOrderCode())
        );
        return toOrderResponse(saved);
    }

    @Transactional
    public int expireDueOrders() {
        return expireDueOrders(DEFAULT_EXPIRE_BATCH_SIZE);
    }

    @Transactional
    public int expireDueOrders(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, MAX_PAGE_SIZE));
        Instant now = Instant.now();
        List<UUID> dueOrderIds = orderRepository.findDuePendingOrderIds(now, limit);
        int expiredCount = 0;

        for (UUID orderId : dueOrderIds) {
            PurchaseOrder locked = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (locked == null) {
                continue;
            }
            if (expireLockedOrder(locked, now)) {
                expiredCount++;
            }
        }

        return expiredCount;
    }

    private PurchaseOrder expireIfDue(PurchaseOrder order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return order;
        }
        Instant now = Instant.now();
        if (order.getExpiresAt() == null || !now.isAfter(order.getExpiresAt())) {
            return order;
        }

        PurchaseOrder locked = orderRepository.findByIdForUpdate(order.getId()).orElse(order);
        expireLockedOrder(locked, now);
        return locked;
    }

    private boolean expireLockedOrder(PurchaseOrder order, Instant now) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        if (order.getExpiresAt() == null || !now.isAfter(order.getExpiresAt())) {
            return false;
        }

        order.setStatus(OrderStatus.EXPIRED);
        PurchaseOrder saved = orderRepository.save(order);
        closePendingPaymentSession(saved);

        auditLogService.recordSystem(
                "order-expiration",
                AuditAction.ORDER_EXPIRED,
                AuditDomain.ORDER,
                AuditResult.SUCCESS,
                "ORDER",
                saved.getId().toString(),
                "Pending order expired after checkout window",
                Map.of(
                        "orderCode", saved.getOrderCode(),
                        "expiresAt", saved.getExpiresAt() == null ? null : saved.getExpiresAt().toString()
                ),
                "order:" + saved.getId(),
                null
        );
        return true;
    }

    private void closePendingPaymentSession(PurchaseOrder order) {
        paymentTransactionRepository.findByOrderIdAndStatus(order.getId(), TransactionStatus.PENDING)
                .forEach(transaction -> {
                    transaction.setStatus(TransactionStatus.FAILED);
                    paymentTransactionRepository.save(transaction);
                    sePayOrderRepository.findByTransactionId(transaction.getId())
                            .ifPresent(sePayOrder -> {
                                if (sePayOrder.getStatus() == SePayOrderStatus.WAITING_PAYMENT
                                        || sePayOrder.getStatus() == SePayOrderStatus.CREATED) {
                                    sePayOrder.setStatus(SePayOrderStatus.EXPIRED);
                                    sePayOrderRepository.save(sePayOrder);
                                }
                            });
                });
    }

    private OrderResponse toOrderResponse(PurchaseOrder order) {
        List<OrderItemResponse> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())
                .stream()
                .map(this::toOrderItemResponse)
                .toList();
        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElse(null);
        SePayOrder sePayOrder = transaction == null
                ? null
                : sePayOrderRepository.findByTransactionId(transaction.getId()).orElse(null);
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                transaction == null ? null : toTransactionResponse(transaction),
                sePayOrder == null ? null : toSePayOrderSummaryResponse(sePayOrder)
        );
    }

    private OrderSummaryResponse toOrderSummaryResponse(PurchaseOrder order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt()
        );
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        String className = item.getClassId() == null
            ? null
            : classOfferingRepository.findById(item.getClassId())
                    .map(classOffering -> classOffering.getClassName())
                    .orElse(null);

        return new OrderItemResponse(
                item.getId(),
                item.getCourseId(),
                item.getClassId(),
                className,
                item.getItemTitle(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getFinalAmount()
        );
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
                transaction.getCreatedAt()
        );
    }

    private SePayOrderSummaryResponse toSePayOrderSummaryResponse(SePayOrder sePayOrder) {
        return new SePayOrderSummaryResponse(
                sePayOrder.getId(),
                sePayOrder.getPaymentCode(),
                sePayOrder.getBankAccountNumber(),
                sePayOrder.getBankName(),
                sePayOrder.getAccountName(),
                sePayOrder.getAmount(),
                sePayOrder.getQrUrl(),
                sePayOrder.getStatus().name(),
                sePayOrder.getExpiresAt(),
                sePayOrder.getMatchedAt()
        );
    }

    private void requireOwnerOrAdmin(UserAccount actor, PurchaseOrder order) {
        if (order.getUserId().equals(actor.getId()) || isAdminOrTmo(actor)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Order access is denied");
    }

    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
