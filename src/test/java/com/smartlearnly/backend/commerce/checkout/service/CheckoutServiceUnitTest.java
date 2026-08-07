package com.smartlearnly.backend.commerce.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.checkout.dto.CheckoutItemType;
import com.smartlearnly.backend.commerce.checkout.dto.CheckoutResponse;
import com.smartlearnly.backend.commerce.entity.OrderItem;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.PurchaseOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.repository.OrderItemRepository;
import com.smartlearnly.backend.commerce.repository.OrderRepository;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.service.SePayPaymentInstructionService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link CheckoutService} covering the most complex branches of
 * the checkout flow: amount validation, order-code collision retry, SePay
 * instruction validation, audit recording and the offline class item path.
 * Pure Mockito/JUnit tests, no Spring context involved.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SePayOrderRepository sePayOrderRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ObjectProvider<SePayPaymentInstructionService> sePayInstructionServices;

    private CheckoutService service;

    @BeforeEach
    void setUp() {
        CheckoutItemService checkoutItemService = new CheckoutItemService(
                courseRepository,
                classOfferingRepository,
                courseEnrollmentRepository,
                classEnrollmentRepository);
        service = new CheckoutService(
                orderRepository,
                orderItemRepository,
                paymentTransactionRepository,
                sePayOrderRepository,
                currentUserService,
                auditLogService,
                sePayInstructionServices,
                checkoutItemService);
        ReflectionTestUtils.setField(service, "checkoutExpiration", Duration.ofMinutes(30));
    }

    @Test
    void checkoutShouldRejectNonPositiveAmount() {
        UserAccount user = user();
        Course course = course();
        course.setDiscountedPrice(new BigDecimal("-1"));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(CheckoutItemType.COURSE, course.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(orderRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void checkoutShouldRetryOrderCodeUntilUnique() {
        UserAccount user = user();
        Course course = course();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        // First generated code collides, second one is unique.
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(true, false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable()).thenReturn(validInstruction());
        when(sePayOrderRepository.save(any(SePayOrder.class))).thenAnswer(invocation -> {
            SePayOrder sePayOrder = invocation.getArgument(0);
            sePayOrder.setId(UUID.randomUUID());
            return sePayOrder;
        });

        service.checkout(CheckoutItemType.COURSE, course.getId(), null);

        verify(orderRepository, times(2)).existsByOrderCode(anyString());
        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderCode())
                .matches("SLP-ORDER-\\d{8}-[0-9A-Z]{6}");
    }

    @Test
    void checkoutShouldRejectBlankSePayInstruction() {
        UserAccount user = user();
        Course course = course();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable())
                .thenReturn(request -> new SePayPaymentInstruction(
                        " ", " ", " ", " ", " ", " ", request.amount(), request.expiresAt()));

        assertThatThrownBy(() -> service.checkout(CheckoutItemType.COURSE, course.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

        verify(sePayOrderRepository, never()).save(any());
    }

    @Test
    void checkoutShouldRejectSePayInstructionAmountMismatch() {
        UserAccount user = user();
        Course course = course();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable())
                .thenReturn(request -> new SePayPaymentInstruction(
                        "SLPCODE1", "SLPCODE1", "123456789", "MBBANK", "SMART LEARNLY",
                        "https://qr.example/1", request.amount().add(BigDecimal.ONE), request.expiresAt()));

        assertThatThrownBy(() -> service.checkout(CheckoutItemType.COURSE, course.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

        verify(sePayOrderRepository, never()).save(any());
    }

    @Test
    void checkoutShouldRecordOrderCreatedAndPaymentCreatedAuditEvents() {
        UserAccount user = user();
        Course course = course();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable()).thenReturn(validInstruction());
        when(sePayOrderRepository.save(any(SePayOrder.class))).thenAnswer(invocation -> {
            SePayOrder sePayOrder = invocation.getArgument(0);
            sePayOrder.setId(UUID.randomUUID());
            return sePayOrder;
        });

        service.checkout(CheckoutItemType.COURSE, course.getId(), null);

        ArgumentCaptor<AuditAction> actionCaptor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditLogService, times(2)).recordUser(
                eq(user), actionCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(actionCaptor.getAllValues())
                .containsExactly(AuditAction.ORDER_CREATED, AuditAction.PAYMENT_CREATED);
    }

    @Test
    void checkoutShouldCreateOrderForOfflineClassItem() {
        UserAccount user = user();
        Course course = course();
        ClassOffering classOffering = classOffering(course.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(classEnrollmentRepository.countByClassIdAndStatus(classOffering.getId(), "active")).thenReturn(0L);
        when(classEnrollmentRepository.findByClassIdAndStudentId(classOffering.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable()).thenReturn(validInstruction());
        when(sePayOrderRepository.save(any(SePayOrder.class))).thenAnswer(invocation -> {
            SePayOrder sePayOrder = invocation.getArgument(0);
            sePayOrder.setId(UUID.randomUUID());
            return sePayOrder;
        });

        CheckoutResponse response = service.checkout(CheckoutItemType.CLASS, course.getId(), classOffering.getId());

        assertThat(response.amount()).isEqualByComparingTo("500000");
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        OrderItem savedItem = itemsCaptor.getValue().get(0);
        assertThat(savedItem.getClassId()).isEqualTo(classOffering.getId());
        assertThat(savedItem.getCourseId()).isEqualTo(course.getId());
        assertThat(savedItem.getFinalAmount()).isEqualByComparingTo("500000");
    }

    private SePayPaymentInstructionService validInstruction() {
        return request -> new SePayPaymentInstruction(
                "SLPCODE1", "SLPCODE1", "123456789", "MBBANK", "SMART LEARNLY",
                "https://qr.example/1", request.amount(), request.expiresAt());
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole("TRAINEE");
        return user;
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Java Backend");
        course.setPrice(new BigDecimal("399000"));
        course.setFree(false);
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }

    private ClassOffering classOffering(UUID courseId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(courseId);
        classOffering.setClassName("Java Backend - Offline");
        classOffering.setPrice(new BigDecimal("500000"));
        classOffering.setStatus(ClassStatus.UPCOMING);
        classOffering.setStartDate(LocalDate.now().plusDays(1));
        classOffering.setMaxStudents(30);
        return classOffering;
    }
}
