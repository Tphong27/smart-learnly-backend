package com.smartlearnly.backend.commerce.order.service;

import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.order.dto.OrderItemResponse;
import com.smartlearnly.backend.commerce.order.dto.OrderResponse;
import com.smartlearnly.backend.commerce.order.dto.OrderSummaryResponse;
import com.smartlearnly.backend.commerce.order.dto.SePayOrderSummaryResponse;
import com.smartlearnly.backend.commerce.transaction.dto.TransactionResponse;
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
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private NotificationService notificationService;

    @Autowired(required = false)
    // Gắn dịch vụ thông báo khi module notification có mặt trong Spring context.
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    // Tìm toàn bộ đơn theo từ khóa/trạng thái cho Admin/TMO với phân trang giới hạn.
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
    // Lấy chi tiết đơn được phép xem và tự cập nhật nếu đơn vừa quá hạn.
    public OrderResponse getOrder(UUID orderId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order was not found"));
        requireOwnerOrAdmin(actor, order);
        order = expireIfDue(order);
        return toOrderResponse(order);
    }

    @Transactional
    // Hủy đơn pending của học viên, đóng phiên thanh toán và ghi audit/thông báo.
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
        emitOrderNotification(
                saved,
                "Order cancelled",
                "Your pending order was cancelled.",
                "order:" + saved.getId() + ":cancelled");
        return toOrderResponse(saved);
    }

    @Transactional
    // Hết hạn một batch mặc định các đơn đã vượt quá cửa sổ checkout.
    public int expireDueOrders() {
        return expireDueOrders(DEFAULT_EXPIRE_BATCH_SIZE);
    }

    @Transactional
    // Khóa và hết hạn tối đa số đơn được yêu cầu trong một lần chạy scheduler.
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

    // Kiểm tra một đơn khi đọc chi tiết và hết hạn dưới khóa nếu thời gian đã qua.
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

    // Chuyển đơn đã khóa sang EXPIRED, đóng thanh toán và phát audit/thông báo một lần.
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
        emitOrderNotification(
                saved,
                "Order expired",
                "Your pending order expired after the checkout window.",
                "order:" + saved.getId() + ":expired");
        return true;
    }

    // Gửi thông báo trạng thái đơn với event key ổn định để chống trùng.
    private void emitOrderNotification(PurchaseOrder order, String title, String body, String eventKey) {
        if (notificationService == null || order == null || order.getUserId() == null) {
            return;
        }
        notificationService.emit(new NotificationCreateCommand(
                order.getUserId(),
                NotificationType.PAYMENT,
                title,
                body,
                "ORDER",
                order.getId(),
                "/orders/" + order.getId(),
                null,
                eventKey,
                NotificationPayloads.of(
                        "orderCode", order.getOrderCode(),
                        "status", order.getStatus() == null ? null : order.getStatus().name())));
    }

    // Đánh dấu giao dịch pending thất bại và phiên SePay liên quan đã hết hiệu lực.
    private void closePendingPaymentSession(PurchaseOrder order) {
        paymentTransactionRepository.findByOrderIdAndStatus(order.getId(), TransactionStatus.PENDING.name())
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

    // Tập hợp item, giao dịch và thông tin SePay thành response chi tiết của đơn.
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

    // Chuyển entity đơn thành DTO gọn cho danh sách quản trị.
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

    // Chuyển item thành DTO và bổ sung tên lớp khi item mua theo lớp mở.
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

    // Chuyển giao dịch mới nhất thành dữ liệu hiển thị kèm chi tiết đơn.
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

    // Chuyển phiên SePay thành thông tin chuyển khoản và trạng thái cho frontend.
    private SePayOrderSummaryResponse toSePayOrderSummaryResponse(SePayOrder sePayOrder) {
        return new SePayOrderSummaryResponse(
                sePayOrder.getId(),
                sePayOrder.getPaymentCode(),
                sePayOrder.getTransferContent(),
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

    // Cho phép chủ đơn hoặc Admin/TMO xem đơn; từ chối mọi người dùng khác.
    private void requireOwnerOrAdmin(UserAccount actor, PurchaseOrder order) {
        if (order.getUserId().equals(actor.getId()) || isAdminOrTmo(actor)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Order access is denied");
    }

    // Kiểm tra vai trò được phép truy cập đơn của toàn hệ thống.
    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }

    // Chuẩn hóa từ khóa tìm kiếm hoặc trả null khi không lọc.
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
