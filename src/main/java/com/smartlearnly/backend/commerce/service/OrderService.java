package com.smartlearnly.backend.commerce.service;

import com.smartlearnly.backend.commerce.dto.OrderItemResponse;
import com.smartlearnly.backend.commerce.dto.OrderResponse;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SePayOrderRepository sePayOrderRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order was not found"));
        requireOwnerOrAdmin(actor, order);
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

        paymentTransactionRepository.findByOrderIdAndStatus(saved.getId(), TransactionStatus.PENDING)
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

        auditLogService.recordUser(
                actor, AuditAction.ORDER_CANCELLED, AuditDomain.ORDER, AuditResult.SUCCESS,
                "ORDER", saved.getId().toString(), "Order was cancelled",
                java.util.Map.of("status", OrderStatus.PENDING.name()),
                java.util.Map.of("status", OrderStatus.CANCELLED.name()),
                java.util.Map.of("orderCode", saved.getOrderCode())
        );
        return toOrderResponse(saved);
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

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getCourseId(),
                item.getClassId(),
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
}
