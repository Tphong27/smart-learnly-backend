package com.smartlearnly.backend.payment.sepay;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SePayPaymentMatchingService {
    private static final Logger log = LoggerFactory.getLogger(SePayPaymentMatchingService.class);
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
    private final Clock clock;

    @Autowired
    public SePayPaymentMatchingService(
            SePayProperties sePayProperties,
            SePayOrderRepository sePayOrderRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CourseEnrollmentService courseEnrollmentService,
            ClassEnrollmentService classEnrollmentService,
            SePayWebhookEventRepository webhookEventRepository,
            SePayInvoiceNumberRepository invoiceNumberRepository
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
                Clock.systemUTC()
        );
    }

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
        this.clock = clock;
    }

    @Transactional
    public void process(SePayWebhookPayload payload) {
        processCandidate(SePayPaymentMatchCandidate.fromWebhook(payload), webhookOutcome(payload.id()));
    }

    @Transactional
    public void processReconciledTransaction(SePayTransactionCandidate transaction) {
        processCandidate(
                SePayPaymentMatchCandidate.fromReconciledTransaction(transaction),
                reconciliationOutcome(transaction.gatewayTransactionId())
        );
    }

    private void processCandidate(SePayPaymentMatchCandidate candidate, MatchOutcomeRecorder outcomeRecorder) {
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
        outcomeRecorder.processed();
    }

    private boolean isInboundPayment(SePayPaymentMatchCandidate payload) {
        return isBlank(payload.transferType()) || "in".equalsIgnoreCase(payload.transferType().trim());
    }

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

    private String paymentCodePrefix() {
        if (isBlank(sePayProperties.getPaymentCodePrefix())) {
            return DEFAULT_PAYMENT_CODE_PREFIX;
        }
        return sePayProperties.getPaymentCodePrefix().trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePaymentCode(String paymentCode) {
        return paymentCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isProcessableSePayOrder(SePayOrder sePayOrder) {
        return sePayOrder.getStatus() == SePayOrderStatus.CREATED
                || sePayOrder.getStatus() == SePayOrderStatus.WAITING_PAYMENT;
    }

    private boolean matchesReceivingAccount(String payloadAccountNumber, String expectedAccountNumber) {
        if (isBlank(payloadAccountNumber)) {
            return true;
        }
        return normalizeAccount(payloadAccountNumber).equals(normalizeAccount(expectedAccountNumber));
    }

    private String normalizeAccount(String accountNumber) {
        return WHITESPACE.matcher(accountNumber == null ? "" : accountNumber).replaceAll("");
    }

    private boolean isAlreadyPaid(
            PaymentTransaction transaction,
            PurchaseOrder order,
            SePayOrder sePayOrder
    ) {
        return transaction.getStatus() == TransactionStatus.SUCCESS
                || order.getStatus() == OrderStatus.PAID
                || sePayOrder.getStatus() == SePayOrderStatus.MATCHED;
    }

    private boolean isProcessableTransaction(PaymentTransaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING
                || transaction.getStatus() == TransactionStatus.PROCESSING;
    }

    private boolean isGatewayTransactionAlreadyUsed(String gatewayTransactionId, UUID currentTransactionId) {
        return !isBlank(gatewayTransactionId)
                && paymentTransactionRepository.existsByGatewayTransactionIdAndIdNot(
                        gatewayTransactionId.trim(),
                        currentTransactionId
                );
    }

    private Instant resolvePaidAt(String transactionDate) {
        if (!isBlank(transactionDate)) {
            String normalizedDate = transactionDate.trim();
            try {
                return Instant.parse(normalizedDate);
            }
            catch (DateTimeParseException exception) {
                // Continue with the formats SePay exposes for webhook and API responses.
            }
            try {
                return OffsetDateTime.parse(normalizedDate).toInstant();
            }
            catch (DateTimeParseException exception) {
                // Continue with the local Vietnam timestamp used by webhook payloads.
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private MatchOutcomeRecorder webhookOutcome(long gatewayEventId) {
        return new MatchOutcomeRecorder() {
            @Override
            public void processed() {
                webhookEventRepository.markProcessed(gatewayEventId);
            }

            @Override
            public void mismatched(String reason) {
                webhookEventRepository.markMismatched(gatewayEventId, reason);
            }

            @Override
            public void failed(String reason) {
                webhookEventRepository.markFailed(gatewayEventId, reason);
            }
        };
    }

    private MatchOutcomeRecorder reconciliationOutcome(String gatewayTransactionId) {
        return new MatchOutcomeRecorder() {
            @Override
            public void processed() {
                logReconciliationOutcome("processed", null);
            }

            @Override
            public void mismatched(String reason) {
                logReconciliationOutcome("mismatched", reason);
            }

            @Override
            public void failed(String reason) {
                logReconciliationOutcome("failed", reason);
            }

            private void logReconciliationOutcome(String outcome, String reason) {
                if (reason == null) {
                    log.info("SePay reconciliation candidate {} gatewayTransactionId={}", outcome, gatewayTransactionId);
                    return;
                }
                log.info(
                        "SePay reconciliation candidate {} gatewayTransactionId={} reason={}",
                        outcome,
                        gatewayTransactionId,
                        reason
                );
            }
        };
    }

    private interface MatchOutcomeRecorder {
        MatchOutcomeRecorder NO_OP = new MatchOutcomeRecorder() {
        };

        default void processed() {
        }

        default void mismatched(String reason) {
        }

        default void failed(String reason) {
        }
    }
}
