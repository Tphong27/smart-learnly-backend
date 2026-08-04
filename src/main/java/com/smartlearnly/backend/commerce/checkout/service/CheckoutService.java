package com.smartlearnly.backend.commerce.checkout.service;

import com.smartlearnly.backend.commerce.checkout.dto.CheckoutItemType;
import com.smartlearnly.backend.commerce.checkout.dto.CheckoutResponse;
import com.smartlearnly.backend.commerce.entity.OrderItem;
import com.smartlearnly.backend.commerce.entity.OrderStatus;
import com.smartlearnly.backend.commerce.entity.PaymentGateway;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.PurchaseOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrderStatus;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.OrderItemRepository;
import com.smartlearnly.backend.commerce.repository.OrderRepository;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.commerce.checkout.service.CheckoutItemService.CheckoutItem;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstructionRequest;
import com.smartlearnly.backend.payment.sepay.service.SePayPaymentInstructionService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CURRENCY = "VND";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SePayOrderRepository sePayOrderRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ObjectProvider<SePayPaymentInstructionService> sePayInstructionServices;
    private final CheckoutItemService checkoutItemService;

    @Value("${app.payment.checkout-expiration:PT30M}")
    private Duration checkoutExpiration;

    /**
     * Tạo đầy đủ một phiên checkout SePay cho học viên đang đăng nhập.
     * Luồng gồm kiểm tra sản phẩm, tạo order/item/transaction, tạo chỉ dẫn chuyển
     * khoản, lưu SePay order và ghi audit. Toàn bộ thao tác dùng chung transaction
     * để không lưu trạng thái dở dang khi một bước thất bại.
     */
    @Transactional
    public CheckoutResponse checkout(CheckoutItemType itemType, UUID courseId, UUID classId) {
        UserAccount user = currentUserService.requireAuthenticatedUser();

        CheckoutItem item = checkoutItemService.resolve(user.getId(), itemType, courseId, classId);

        BigDecimal totalAmount = item.finalAmount();
        if (totalAmount.signum() <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Checkout amount must be greater than 0");
        }

        Instant expiresAt = Instant.now().plus(checkoutExpiration);

        PurchaseOrder order = createOrder(user.getId(), totalAmount, expiresAt);

        OrderItem orderItem = createOrderItem(order.getId(), item);
        orderItemRepository.saveAll(List.of(orderItem));

        PaymentTransaction transaction = createPendingTransaction(
                user.getId(),
                order.getId(),
                item,
                totalAmount,
                expiresAt);

        SePayPaymentInstruction instruction = createPaymentInstruction(
                order,
                transaction,
                totalAmount,
                expiresAt);

        SePayOrder sePayOrder = createSePayOrder(
                order.getId(),
                transaction.getId(),
                instruction,
                totalAmount,
                expiresAt);

        recordCheckoutAudit(user, order, transaction, itemType, courseId, classId, totalAmount);

        return new CheckoutResponse(
                order.getId(),
                order.getOrderCode(),
                transaction.getId(),
                PaymentGateway.SEPAY.name(),
                sePayOrder.getPaymentCode(),
                sePayOrder.getTransferContent(),
                sePayOrder.getAmount(),
                order.getCurrency(),
                sePayOrder.getBankAccountNumber(),
                sePayOrder.getBankName(),
                sePayOrder.getAccountName(),
                sePayOrder.getQrUrl(),
                order.getStatus().name(),
                order.getExpiresAt());
    }

    /** Tạo order PENDING với mã duy nhất và thời điểm hết hạn của checkout. */
    private PurchaseOrder createOrder(UUID userId, BigDecimal totalAmount, Instant expiresAt) {
        PurchaseOrder order = new PurchaseOrder();
        order.setUserId(userId);
        order.setOrderCode(generateOrderCode());
        order.setTotalAmount(totalAmount);
        order.setCurrency(CURRENCY);
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(expiresAt);
        return orderRepository.save(order);
    }

    /** Chụp thông tin sản phẩm và giá tại thời điểm mua vào một order item. */
    private OrderItem createOrderItem(UUID orderId, CheckoutItem item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setCourseId(item.courseId());
        orderItem.setClassId(item.classId());
        orderItem.setItemTitle(item.title());
        orderItem.setUnitPrice(item.unitPrice());
        orderItem.setDiscountAmount(item.discountAmount());
        orderItem.setFinalAmount(item.finalAmount());
        return orderItem;
    }

    /** Tạo giao dịch SePay PENDING liên kết với order và sản phẩm đang mua. */
    private PaymentTransaction createPendingTransaction(UUID userId, UUID orderId, CheckoutItem item,
            BigDecimal totalAmount, Instant expiresAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUserId(userId);
        transaction.setCourseId(item.courseId());
        transaction.setClassId(item.classId());
        transaction.setOrderId(orderId);
        transaction.setAmount(totalAmount);
        transaction.setCurrency(CURRENCY);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setPaymentGateway(PaymentGateway.SEPAY);
        transaction.setExpiresAt(expiresAt);
        transaction.setDescription("Smart Learnly order " + orderId);
        return paymentTransactionRepository.save(transaction);
    }

    /**
     * Yêu cầu adapter SePay tạo mã thanh toán, nội dung chuyển khoản và QR.
     * Báo lỗi dịch vụ ngoài nếu ứng dụng chưa cấu hình adapter.
     */
    private SePayPaymentInstruction createPaymentInstruction(
            PurchaseOrder order,
            PaymentTransaction transaction,
            BigDecimal totalAmount,
            Instant expiresAt) {
        SePayPaymentInstructionService instructionService = sePayInstructionServices.getIfAvailable();

        if (instructionService == null) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay payment instruction service is not configured");
        }

        return instructionService.createInstruction(new SePayPaymentInstructionRequest(
                order.getId(),
                order.getOrderCode(),
                transaction.getId(),
                totalAmount,
                order.getCurrency(),
                expiresAt));
    }

    /**
     * Kiểm tra chỉ dẫn SePay rồi lưu phiên chờ thanh toán.
     * Số tiền từ adapter phải khớp chính xác với tổng tiền của order.
     */
    private SePayOrder createSePayOrder(
            UUID orderId,
            UUID transactionId,
            SePayPaymentInstruction instruction,
            BigDecimal expectedAmount,
            Instant expectedExpiresAt) {
        if (instruction == null
                || isBlank(instruction.paymentCode())
                || isBlank(instruction.transferContent())
                || isBlank(instruction.bankAccountNumber())
                || isBlank(instruction.bankName())
                || isBlank(instruction.accountName())
                || isBlank(instruction.qrUrl())) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay payment instruction is invalid");
        }

        if (instruction.amount() == null || instruction.amount().compareTo(expectedAmount) != 0) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay instruction amount mismatch");
        }

        SePayOrder sePayOrder = new SePayOrder();
        sePayOrder.setOrderId(orderId);
        sePayOrder.setTransactionId(transactionId);
        sePayOrder.setPaymentCode(instruction.paymentCode());
        sePayOrder.setTransferContent(instruction.transferContent());
        sePayOrder.setBankAccountNumber(instruction.bankAccountNumber());
        sePayOrder.setBankName(instruction.bankName());
        sePayOrder.setAccountName(instruction.accountName());
        sePayOrder.setAmount(instruction.amount());
        sePayOrder.setQrUrl(instruction.qrUrl());
        sePayOrder.setStatus(SePayOrderStatus.WAITING_PAYMENT);
        sePayOrder.setExpiresAt(
                instruction.expiresAt() == null ? expectedExpiresAt : instruction.expiresAt());

        return sePayOrderRepository.save(sePayOrder);
    }

    /** Ghi hai audit event riêng cho việc tạo order và tạo payment transaction. */
    private void recordCheckoutAudit(
            UserAccount user,
            PurchaseOrder order,
            PaymentTransaction transaction,
            CheckoutItemType itemType,
            UUID courseId,
            UUID classId,
            BigDecimal amount
    ) {
        Map<String, Object> orderMetadata = new LinkedHashMap<>();
        orderMetadata.put("amount", amount);
        orderMetadata.put("currency", CURRENCY);
        orderMetadata.put("itemType", itemType.name());
        orderMetadata.put("courseId", courseId);
        if (classId != null) {
            orderMetadata.put("classId", classId);
        }

        auditLogService.recordUser(
                user,
                AuditAction.ORDER_CREATED,
                AuditDomain.ORDER,
                AuditResult.SUCCESS,
                "ORDER",
                order.getId().toString(),
                "Order was created",
                null,
                null,
                orderMetadata
        );

        auditLogService.recordUser(
                user,
                AuditAction.PAYMENT_CREATED,
                AuditDomain.PAYMENT,
                AuditResult.SUCCESS,
                "PAYMENT_TRANSACTION",
                transaction.getId().toString(),
                "Payment transaction was created",
                null,
                null,
                Map.of(
                        "orderId", order.getId(),
                        "gateway", PaymentGateway.SEPAY.name()
                )
        );
    }

    /** Tạo mã order theo ngày UTC và thử lại nếu mã ngẫu nhiên đã tồn tại. */
    private String generateOrderCode() {
        String code;

        do {
            code = "SLP-ORDER-"
                    + ORDER_DATE_FORMAT.format(LocalDate.now(ZoneOffset.UTC))
                    + "-"
                    + randomBase36(6);
        } while (orderRepository.existsByOrderCode(code));

        return code;
    }

    /** Tạo chuỗi chữ-số ngẫu nhiên viết hoa dùng làm hậu tố mã order. */
    private String randomBase36(int length) {
        StringBuilder builder = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            builder.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }

        return builder.toString().toUpperCase(Locale.ROOT);
    }

    /** Kiểm tra chuỗi null, rỗng hoặc chỉ chứa khoảng trắng. */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
