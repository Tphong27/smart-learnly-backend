package com.smartlearnly.backend.payment.sepay.service;

import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentMatchCandidate;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionCandidate;
import com.smartlearnly.backend.payment.sepay.dto.SePayWebhookPayload;
import com.smartlearnly.backend.payment.sepay.repository.SePayInvoiceNumberRepository;
import com.smartlearnly.backend.payment.sepay.repository.SePayWebhookEventRepository;

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
import com.smartlearnly.backend.enrollment.service.ClassEnrollmentService;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SePayPaymentMatchingService {
    private static final String DEFAULT_PAYMENT_CODE_PREFIX = "SLP";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter SEPAY_TRANSACTION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SePayProperties sePayProperties;
    private final SePayOrderRepository sePayOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ClassEnrollmentService classEnrollmentService;
    private final SePayWebhookEventRepository webhookEventRepository;
    private final SePayInvoiceNumberRepository invoiceNumberRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private NotificationService notificationService;

    @Autowired
    // Khởi tạo matching service production với đồng hồ UTC của hệ thống.
    public SePayPaymentMatchingService(
            SePayProperties sePayProperties,
            SePayOrderRepository sePayOrderRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CourseEnrollmentService courseEnrollmentService,
            ClassEnrollmentService classEnrollmentService,
            SePayWebhookEventRepository webhookEventRepository,
            SePayInvoiceNumberRepository invoiceNumberRepository,
            AuditLogService auditLogService
    ) {
        this(
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
                Clock.systemUTC()
        );
    }

    // Cho phép test cố định thời gian thanh toán mà vẫn dùng cùng toàn bộ nghiệp vụ matching.
    SePayPaymentMatchingService(
            SePayProperties sePayProperties,
            SePayOrderRepository sePayOrderRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CourseEnrollmentService courseEnrollmentService,
            ClassEnrollmentService classEnrollmentService,
            SePayWebhookEventRepository webhookEventRepository,
            SePayInvoiceNumberRepository invoiceNumberRepository,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.sePayProperties = sePayProperties;
        this.sePayOrderRepository = sePayOrderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.classEnrollmentService = classEnrollmentService;
        this.webhookEventRepository = webhookEventRepository;
        this.invoiceNumberRepository = invoiceNumberRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Autowired(required = false)
    // Gắn dịch vụ thông báo khi module notification có mặt trong Spring context.
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional
    // Nhận webhook đã xác thực, ghi audit và chạy quy trình ghép thanh toán idempotent.
    public void process(SePayWebhookPayload payload) {
        SePayPaymentMatchCandidate candidate = SePayPaymentMatchCandidate.fromWebhook(payload);
        auditLogService.recordPaymentProvider(
                "sepay", AuditAction.PAYMENT_CALLBACK_RECEIVED, AuditResult.SUCCESS,
                "GATEWAY_EVENT", Long.toString(payload.id()), "SePay payment callback was received",
                safePaymentMetadata(candidate), correlationId(payload.id()), null
        );
        processCandidate(candidate, webhookOutcome(payload.id()), false);
    }

    @Transactional
    // Đưa giao dịch lấy từ API đối soát vào cùng quy trình matching với webhook.
    public void processReconciledTransaction(SePayTransactionCandidate transaction) {
        processCandidate(
                SePayPaymentMatchCandidate.fromReconciledTransaction(transaction),
                MatchOutcomeRecorder.NO_OP,
                true
        );
    }

    // Kiểm tra ứng viên, khóa dữ liệu liên quan và hoàn tất đơn/giao dịch/ghi danh khi khớp.
    private void processCandidate(
            SePayPaymentMatchCandidate candidate,
            MatchOutcomeRecorder outcomeRecorder,
            boolean reconciled
    ) {
        if (!isInboundPayment(candidate)) {
            outcomeRecorder.mismatched("SePay transfer is not inbound");
            return;
        }

        Optional<String> paymentCode = resolvePaymentCode(candidate);
        if (paymentCode.isEmpty()) {
            outcomeRecorder.mismatched("SePay payment code was not found");
            return;
        }

        SePayOrder sePayOrder = sePayOrderRepository.findByPaymentCodeForUpdate(paymentCode.get()).orElse(null);
        if (sePayOrder == null) {
            outcomeRecorder.mismatched("SePay payment code did not match an order");
            return;
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findByIdForUpdate(sePayOrder.getTransactionId())
                .orElse(null);
        PurchaseOrder order = orderRepository.findByIdForUpdate(sePayOrder.getOrderId()).orElse(null);
        if (transaction == null || order == null) {
            outcomeRecorder.failed("Matched payment references missing local records");
            return;
        }
        if (isAlreadyPaid(transaction, order, sePayOrder)) {
            outcomeRecorder.processed();
            return;
        }
        if (!isProcessableSePayOrder(sePayOrder)) {
            outcomeRecorder.mismatched("SePay order is not waiting for payment");
            return;
        }
        if (candidate.transferAmount() == null || candidate.transferAmount().compareTo(sePayOrder.getAmount()) != 0) {
            outcomeRecorder.mismatched("SePay payment amount did not match");
            return;
        }
        if (!matchesReceivingAccount(candidate.accountNumber(), sePayOrder.getBankAccountNumber())) {
            outcomeRecorder.mismatched("SePay receiving account did not match");
            return;
        }
        if (isGatewayTransactionAlreadyUsed(candidate.gatewayTransactionId(), transaction.getId())) {
            outcomeRecorder.mismatched("SePay gateway transaction was already used");
            return;
        }
        if (!isProcessableTransaction(transaction) || order.getStatus() != OrderStatus.PENDING) {
            outcomeRecorder.mismatched("Matched payment is not pending");
            return;
        }

        Instant paidAt = resolvePaidAt(candidate.transactionDate());
        transaction.setStatus(TransactionStatus.SUCCESS);
        if (candidate.gatewayEventId() != null) {
            transaction.setGatewayEventId(candidate.gatewayEventId());
        }
        if (!isBlank(candidate.gatewayTransactionId())) {
            transaction.setGatewayTransactionId(candidate.gatewayTransactionId().trim());
        }
        if (isBlank(transaction.getInvoiceNumber())) {
            transaction.setInvoiceNumber(invoiceNumberRepository.nextInvoiceNumber());
        }
        transaction.setPaidAt(paidAt);
        paymentTransactionRepository.saveAndFlush(transaction);

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        orderRepository.saveAndFlush(order);

        sePayOrder.setStatus(SePayOrderStatus.MATCHED);
        sePayOrder.setMatchedAt(paidAt);
        sePayOrderRepository.saveAndFlush(sePayOrder);

        grantEnrollments(order.getId(), order.getUserId(), transaction.getId());
        emitPaymentSuccessNotification(order, transaction);
        if (reconciled) {
            auditLogService.recordSystem(
                    "sepay-reconciliation", AuditAction.PAYMENT_RECONCILED, AuditDomain.PAYMENT, AuditResult.SUCCESS,
                    "PAYMENT_TRANSACTION", transaction.getId().toString(), "SePay payment was reconciled",
                    safePaymentMetadata(candidate), "transaction:" + transaction.getId(), null
            );
        }
        else {
            auditLogService.recordPaymentProvider(
                    "sepay", AuditAction.PAYMENT_SUCCEEDED, AuditResult.SUCCESS,
                    "PAYMENT_TRANSACTION", transaction.getId().toString(), "SePay payment succeeded",
                    safePaymentMetadata(candidate), correlationId(candidate.gatewayEventId()), null
            );
        }
        outcomeRecorder.processed();
    }

    // Chỉ chấp nhận giao dịch tiền vào tài khoản nhận của hệ thống.
    private boolean isInboundPayment(SePayPaymentMatchCandidate payload) {
        return !isBlank(payload.transferType())
                && "in".equalsIgnoreCase(payload.transferType().trim());
    }

    // Lấy mã thanh toán từ trường code hoặc duy nhất một mã xuất hiện trong nội dung.
    private Optional<String> resolvePaymentCode(SePayPaymentMatchCandidate payload) {
        if (!isBlank(payload.code())) {
            return Optional.of(normalizePaymentCode(payload.code()));
        }

        Set<String> candidates = extractPaymentCodeCandidates(payload.content());
        if (candidates.size() != 1) {
            return Optional.empty();
        }
        return candidates.stream().findFirst();
    }

    // Trích các mã thanh toán đúng tiền tố và đúng độ dài từ nội dung chuyển khoản.
    private Set<String> extractPaymentCodeCandidates(String content) {
        Set<String> candidates = new LinkedHashSet<>();
        if (isBlank(content)) {
            return candidates;
        }

        Pattern pattern = Pattern.compile(Pattern.quote(paymentCodePrefix()) + "[0-9A-Z]{12}");
        Matcher matcher = pattern.matcher(content.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            candidates.add(matcher.group());
        }
        return candidates;
    }

    // Lấy tiền tố matching đã chuẩn hóa hoặc dùng SLP khi chưa cấu hình.
    private String paymentCodePrefix() {
        if (isBlank(sePayProperties.getPaymentCodePrefix())) {
            return DEFAULT_PAYMENT_CODE_PREFIX;
        }
        return sePayProperties.getPaymentCodePrefix().trim().toUpperCase(Locale.ROOT);
    }

    // Chuẩn hóa mã thanh toán về chữ hoa và bỏ khoảng trắng ngoài.
    private String normalizePaymentCode(String paymentCode) {
        return paymentCode.trim().toUpperCase(Locale.ROOT);
    }

    // Kiểm tra bản ghi SePay còn đang ở trạng thái chờ có thể nhận thanh toán.
    private boolean isProcessableSePayOrder(SePayOrder sePayOrder) {
        return sePayOrder.getStatus() == SePayOrderStatus.CREATED
                || sePayOrder.getStatus() == SePayOrderStatus.WAITING_PAYMENT;
    }

    // So sánh tài khoản nhận sau khi loại khoảng trắng để tránh sai khác định dạng.
    private boolean matchesReceivingAccount(String payloadAccountNumber, String expectedAccountNumber) {
        if (isBlank(payloadAccountNumber) || isBlank(expectedAccountNumber)) {
            return false;
        }
        return normalizeAccount(payloadAccountNumber).equals(normalizeAccount(expectedAccountNumber));
    }

    // Loại mọi khoảng trắng khỏi số tài khoản trước khi đối chiếu.
    private String normalizeAccount(String accountNumber) {
        return WHITESPACE.matcher(accountNumber == null ? "" : accountNumber).replaceAll("");
    }

    // Nhận diện đơn đã hoàn tất để webhook gửi lại vẫn được xử lý idempotent.
    private boolean isAlreadyPaid(
            PaymentTransaction transaction,
            PurchaseOrder order,
            SePayOrder sePayOrder
    ) {
        return transaction.getStatus() == TransactionStatus.SUCCESS
                || order.getStatus() == OrderStatus.PAID
                || sePayOrder.getStatus() == SePayOrderStatus.MATCHED;
    }

    // Chỉ cho phép cập nhật giao dịch đang pending hoặc processing.
    private boolean isProcessableTransaction(PaymentTransaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING
                || transaction.getStatus() == TransactionStatus.PROCESSING;
    }

    // Chặn một giao dịch ngân hàng được dùng để thanh toán cho nhiều đơn khác nhau.
    private boolean isGatewayTransactionAlreadyUsed(String gatewayTransactionId, UUID currentTransactionId) {
        return !isBlank(gatewayTransactionId)
                && paymentTransactionRepository.existsByGatewayTransactionIdAndIdNot(
                        gatewayTransactionId.trim(),
                        currentTransactionId
                );
    }

    // Chuẩn hóa các định dạng thời gian SePay về Instant, fallback sang thời gian hiện tại.
    private Instant resolvePaidAt(String transactionDate) {
        if (!isBlank(transactionDate)) {
            String normalizedDate = transactionDate.trim();
            try {
                return Instant.parse(normalizedDate);
            }
            catch (DateTimeParseException exception) {
                // Thử tiếp định dạng offset mà API SePay có thể trả về.
            }
            try {
                return OffsetDateTime.parse(normalizedDate).toInstant();
            }
            catch (DateTimeParseException exception) {
                // Thử tiếp định dạng giờ Việt Nam không kèm múi giờ của webhook.
            }
            try {
                return LocalDateTime.parse(normalizedDate, SEPAY_TRANSACTION_DATE_FORMAT)
                        .atZone(VIETNAM_ZONE)
                        .toInstant();
            }
            catch (DateTimeParseException exception) {
                return Instant.now(clock);
            }
        }
        return Instant.now(clock);
    }

    // Kích hoạt quyền học khóa hoặc lớp cho từng item sau khi thanh toán thành công.
    private void grantEnrollments(UUID orderId, UUID studentId, UUID transactionId) {
        for (OrderItem item : orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            if (item.getClassId() == null) {
                courseEnrollmentService.grantPaidCourseEnrollment(
                        studentId,
                        item.getCourseId(),
                        transactionId
                );
            }
            else {
                classEnrollmentService.grantPaidClassEnrollment(
                        studentId,
                        item.getClassId(),
                        item.getFinalAmount(),
                        transactionId
                );
            }
        }
    }

    // Gửi một thông báo thanh toán thành công có deduplication key cho học viên.
    private void emitPaymentSuccessNotification(PurchaseOrder order, PaymentTransaction transaction) {
        if (notificationService == null || order == null || order.getUserId() == null || transaction == null) {
            return;
        }
        notificationService.emit(new NotificationCreateCommand(
                order.getUserId(),
                NotificationType.PAYMENT,
                "Payment confirmed",
                "Your payment has been confirmed.",
                "PAYMENT_TRANSACTION",
                transaction.getId(),
                "/orders/" + order.getId(),
                null,
                "payment-transaction:" + transaction.getId() + ":success",
                NotificationPayloads.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "transactionId", transaction.getId(),
                        "amount", transaction.getAmount(),
                        "status", transaction.getStatus() == null ? null : transaction.getStatus().name())));
    }

    // Kiểm tra chuỗi bị thiếu hoặc chỉ chứa khoảng trắng.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Chỉ đưa metadata không nhạy cảm vào audit log của thanh toán.
    private Map<String, Object> safePaymentMetadata(SePayPaymentMatchCandidate candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!isBlank(candidate.code())) metadata.put("paymentCode", normalizePaymentCode(candidate.code()));
        if (candidate.transferAmount() != null) metadata.put("amount", candidate.transferAmount());
        if (!isBlank(candidate.transferType())) metadata.put("transferType", candidate.transferType().trim());
        if (!isBlank(candidate.gatewayTransactionId())) {
            metadata.put("gatewayTransactionId", candidate.gatewayTransactionId().trim());
        }
        return metadata;
    }

    // Tạo correlation id ổn định để liên kết audit log của cùng một webhook.
    private String correlationId(Long gatewayEventId) {
        return gatewayEventId == null ? null : "sepay:" + gatewayEventId;
    }

    // Tạo bộ ghi kết quả để đồng bộ trạng thái webhook và audit theo từng nhánh matching.
    private MatchOutcomeRecorder webhookOutcome(long gatewayEventId) {
        return new MatchOutcomeRecorder() {
            @Override
            // Đánh dấu sự kiện webhook đã hoàn tất thành công.
            public void processed() {
                webhookEventRepository.markProcessed(gatewayEventId);
            }

            @Override
            // Ghi trạng thái không khớp cùng lý do an toàn cho màn hình giám sát.
            public void mismatched(String reason) {
                webhookEventRepository.markMismatched(gatewayEventId, reason);
                auditLogService.recordPaymentProvider(
                        "sepay", AuditAction.PAYMENT_MISMATCHED, AuditResult.FAILURE,
                        "GATEWAY_EVENT", Long.toString(gatewayEventId), reason,
                        null, correlationId(gatewayEventId), "PAYMENT_MISMATCHED"
                );
            }

            @Override
            // Ghi trạng thái thất bại và audit khi dữ liệu local không thể hoàn tất.
            public void failed(String reason) {
                webhookEventRepository.markFailed(gatewayEventId, reason);
                auditLogService.recordPaymentProvider(
                        "sepay", AuditAction.PAYMENT_FAILED, AuditResult.FAILURE,
                        "GATEWAY_EVENT", Long.toString(gatewayEventId), reason,
                        null, correlationId(gatewayEventId), "PAYMENT_FAILED"
                );
            }
        };
    }

    private interface MatchOutcomeRecorder {
        MatchOutcomeRecorder NO_OP = new MatchOutcomeRecorder() {
        };

        // Mặc định không ghi kết quả cho giao dịch đến từ luồng đối soát.
        default void processed() {
        }

        // Mặc định bỏ qua trạng thái mismatch khi không có webhook event tương ứng.
        default void mismatched(String reason) {
        }

        // Mặc định bỏ qua trạng thái failed khi không có webhook event tương ứng.
        default void failed(String reason) {
        }
    }
}
