package com.smartlearnly.backend.commerce.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.CheckoutResponse;
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
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.payment.sepay.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.SePayPaymentInstructionRequest;
import com.smartlearnly.backend.payment.sepay.SePayPaymentInstructionService;
import com.smartlearnly.backend.commerce.dto.CheckoutItemType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
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
    private final CourseRepository courseRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ObjectProvider<SePayPaymentInstructionService> sePayInstructionServices;

    @Value("${app.payment.checkout-expiration:PT30M}")
    private Duration checkoutExpiration;

    @Transactional
    public CheckoutResponse checkout(CheckoutItemType itemType, UUID courseId, UUID classId) {
        UserAccount user = currentUserService.requireAuthenticatedUser();

        OrderItemSnapshot snapshot = toSnapshot(user.getId(), itemType, courseId, classId);

        BigDecimal totalAmount = snapshot.finalAmount();
        if (totalAmount.signum() <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Checkout amount must be greater than 0");
        }

        Instant expiresAt = Instant.now().plus(checkoutExpiration);

        PurchaseOrder order = createOrder(user.getId(), totalAmount, expiresAt);

        OrderItem orderItem = createOrderItem(order.getId(), snapshot);
        orderItemRepository.saveAll(List.of(orderItem));

        PaymentTransaction transaction = createPendingTransaction(
                user.getId(),
                order.getId(),
                snapshot,
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

        Map<String, Object> orderMetadata = new LinkedHashMap<>();
        orderMetadata.put("amount", totalAmount);
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
                orderMetadata);

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
                        "gateway", PaymentGateway.SEPAY.name()));

        return new CheckoutResponse(
                order.getId(),
                order.getOrderCode(),
                transaction.getId(),
                PaymentGateway.SEPAY.name(),
                sePayOrder.getPaymentCode(),
                sePayOrder.getAmount(),
                order.getCurrency(),
                sePayOrder.getBankAccountNumber(),
                sePayOrder.getBankName(),
                sePayOrder.getAccountName(),
                sePayOrder.getQrUrl(),
                order.getStatus().name(),
                order.getExpiresAt());
    }

    private OrderItemSnapshot toSnapshot(UUID studentId, CheckoutItemType itemType, UUID courseId, UUID classId) {
        Course course = requirePublishedCourse(courseId);

        if (itemType == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Checkout item type is required");
        }

        return switch (itemType) {
            case COURSE -> toCourseSnapshot(studentId, course, classId);
            case CLASS -> toClassSnapshot(studentId, course, classId);
        };
    }

    private OrderItemSnapshot toCourseSnapshot(UUID studentId, Course course, UUID classId) {
        if (classId != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Class must not be supplied for an online course checkout");
        }

        requirePaidCourse(course);
        rejectExistingCourseAccess(studentId, course.getId());

        BigDecimal unitPrice = money(course.getPrice());
        BigDecimal finalAmount = resolveFinalAmount(course);
        BigDecimal discountAmount = unitPrice.subtract(finalAmount);

        return new OrderItemSnapshot(
                course.getId(),
                null,
                course.getTitle(),
                unitPrice,
                discountAmount,
                finalAmount);
    }

    private OrderItemSnapshot toClassSnapshot(UUID studentId, Course course, UUID classId) {
        if (classId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Class is required for an offline class checkout");
        }

        ClassOffering classOffering = requireSellableClass(classId, course.getId());

        rejectExistingClassAccess(studentId, classOffering.getId());

        BigDecimal classPrice = classOffering.getPrice();

        if (classPrice == null || classPrice.signum() <= 0) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE,
                    "Class does not have a valid registration price");
        }

        BigDecimal finalAmount = money(classPrice);

        return new OrderItemSnapshot(
                course.getId(),
                classOffering.getId(),
                course.getTitle() + " - " + classOffering.getClassName(),
                finalAmount,
                BigDecimal.ZERO,
                finalAmount);
    }

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

    private OrderItem createOrderItem(UUID orderId, OrderItemSnapshot snapshot) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setCourseId(snapshot.courseId());
        orderItem.setClassId(snapshot.classId());
        orderItem.setItemTitle(snapshot.itemTitle());
        orderItem.setUnitPrice(snapshot.unitPrice());
        orderItem.setDiscountAmount(snapshot.discountAmount());
        orderItem.setFinalAmount(snapshot.finalAmount());
        return orderItem;
    }

    private PaymentTransaction createPendingTransaction(UUID userId, UUID orderId, OrderItemSnapshot snapshot,
            BigDecimal totalAmount, Instant expiresAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUserId(userId);
        transaction.setCourseId(snapshot.courseId());
        transaction.setClassId(snapshot.classId());
        transaction.setOrderId(orderId);
        transaction.setAmount(totalAmount);
        transaction.setCurrency(CURRENCY);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setPaymentGateway(PaymentGateway.SEPAY);
        transaction.setExpiresAt(expiresAt);
        transaction.setDescription("Smart Learnly order " + orderId);
        return paymentTransactionRepository.save(transaction);
    }

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

    private SePayOrder createSePayOrder(
            UUID orderId,
            UUID transactionId,
            SePayPaymentInstruction instruction,
            BigDecimal expectedAmount,
            Instant expectedExpiresAt) {
        if (instruction == null
                || isBlank(instruction.paymentCode())
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

    private Course requirePublishedCourse(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Course was not found"));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_ENROLLABLE);
        }

        return course;
    }

    private void requirePaidCourse(Course course) {
        if (isFree(course)) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_ENROLLABLE,
                    "Free courses must use the free enrollment flow");
        }
    }

    private ClassOffering requireSellableClass(UUID classId, UUID courseId) {
        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));

        if (!courseId.equals(classOffering.getCourseId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Class must belong to the selected course");
        }

        if (classOffering.getStatus() != ClassStatus.UPCOMING) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Only upcoming classes can be registered");
        }

        if (classOffering.getStartDate() == null
                || classOffering.getStartDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Class registration is no longer available");
        }

        if (classOffering.getPrice() == null
                || classOffering.getPrice().signum() < 0) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Class price is not configured");
        }

        long activeCount = classEnrollmentRepository.countByClassIdAndStatus(classOffering.getId(), "active");

        if (activeCount >= classOffering.getMaxStudents()) {
            throw new BusinessException(ErrorCode.CLASS_FULL);
        }

        return classOffering;
    }

    private void rejectExistingCourseAccess(UUID studentId, UUID courseId) {
        CourseEnrollment enrollment = courseEnrollmentRepository
                .findByCourseIdAndStudentId(courseId, studentId)
                .orElse(null);

        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Learner already has access to this course");
        }
    }

    private void rejectExistingClassAccess(UUID studentId, UUID classId) {
        ClassEnrollment enrollment = classEnrollmentRepository
                .findByClassIdAndStudentId(classId, studentId)
                .orElse(null);

        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Learner already has access to this class");
        }
    }

    private boolean hasAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED;
    }

    private BigDecimal resolveFinalAmount(Course course) {
        BigDecimal unitPrice = money(course.getPrice());
        BigDecimal discountedPrice = course.getDiscountedPrice();

        if (discountedPrice == null) {
            return unitPrice;
        }

        BigDecimal finalAmount = money(discountedPrice);

        if (finalAmount.compareTo(unitPrice) > 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Discounted price cannot exceed course price");
        }

        return finalAmount;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isFree(Course course) {
        BigDecimal price = money(course.getPrice());
        return Boolean.TRUE.equals(course.getFree()) || price.compareTo(BigDecimal.ZERO) == 0;
    }

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

    private String randomBase36(int length) {
        StringBuilder builder = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            builder.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }

        return builder.toString().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record OrderItemSnapshot(
            UUID courseId,
            UUID classId,
            String itemTitle,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal finalAmount) {
    }
}