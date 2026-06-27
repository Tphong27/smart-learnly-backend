package com.smartlearnly.backend.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.CheckoutResponse;
import com.smartlearnly.backend.commerce.entity.Cart;
import com.smartlearnly.backend.commerce.entity.CartItem;
import com.smartlearnly.backend.commerce.entity.OrderItem;
import com.smartlearnly.backend.commerce.entity.PaymentGateway;
import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.PurchaseOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrderStatus;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import com.smartlearnly.backend.commerce.repository.CartItemRepository;
import com.smartlearnly.backend.commerce.repository.CartRepository;
import com.smartlearnly.backend.commerce.repository.OrderItemRepository;
import com.smartlearnly.backend.commerce.repository.OrderRepository;
import com.smartlearnly.backend.commerce.repository.PaymentTransactionRepository;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.payment.sepay.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.SePayPaymentInstructionService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
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
        service = new CheckoutService(
                cartRepository,
                cartItemRepository,
                orderRepository,
                orderItemRepository,
                paymentTransactionRepository,
                sePayOrderRepository,
                courseRepository,
                classOfferingRepository,
                courseEnrollmentRepository,
                classEnrollmentRepository,
                currentUserService,
                auditLogService,
                sePayInstructionServices
        );
        ReflectionTestUtils.setField(service, "checkoutExpiration", Duration.ofMinutes(30));
    }

    @Test
    void checkoutShouldCreateOrderSnapshotPendingTransactionAndSePayOrder() {
        UserAccount user = user();
        Cart cart = cart(user.getId());
        Course course = paidPublishedCourse();
        CartItem cartItem = cartItem(cart.getId(), course.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByIdAndUserId(cart.getId(), user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())).thenReturn(List.of(cartItem));
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
        when(sePayInstructionServices.getIfAvailable()).thenReturn(request ->
                new SePayPaymentInstruction(
                        "SLPABC123",
                        "123456789",
                        "MBBANK",
                        "SMART LEARNLY",
                        "https://qr.example/SLPABC123",
                        request.amount(),
                        request.expiresAt()
                ));
        when(sePayOrderRepository.save(any(SePayOrder.class))).thenAnswer(invocation -> {
            SePayOrder sePayOrder = invocation.getArgument(0);
            sePayOrder.setId(UUID.randomUUID());
            return sePayOrder;
        });

        CheckoutResponse response = service.checkout(cart.getId());

        assertThat(response.orderId()).isNotNull();
        assertThat(response.transactionId()).isNotNull();
        assertThat(response.paymentGateway()).isEqualTo("SEPAY");
        assertThat(response.paymentCode()).isEqualTo("SLPABC123");
        assertThat(response.amount()).isEqualByComparingTo("399000");
        assertThat(response.status()).isEqualTo("PENDING");

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transactionCaptor.getValue().getPaymentGateway()).isEqualTo(PaymentGateway.SEPAY);

        ArgumentCaptor<SePayOrder> sePayOrderCaptor = ArgumentCaptor.forClass(SePayOrder.class);
        verify(sePayOrderRepository).save(sePayOrderCaptor.capture());
        assertThat(sePayOrderCaptor.getValue().getStatus()).isEqualTo(SePayOrderStatus.WAITING_PAYMENT);
        assertThat(sePayOrderCaptor.getValue().getAmount()).isEqualByComparingTo("399000");

        verify(cartItemRepository).deleteByCartId(cart.getId());
    }

    @Test
    void checkoutShouldRejectCourseThatAlreadyHasActiveEnrollment() {
        UserAccount user = user();
        Cart cart = cart(user.getId());
        Course course = paidPublishedCourse();
        CartItem cartItem = cartItem(cart.getId(), course.getId());
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByIdAndUserId(cart.getId(), user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())).thenReturn(List.of(cartItem));
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.checkout(cart.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(orderRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).save(any());
        verify(sePayOrderRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkoutShouldUseClassPriceInsteadOfCoursePrice() {
        UserAccount user = user();
        Cart cart = cart(user.getId());
        Course course = paidPublishedCourse();
        ClassOffering classOffering = paidClass(course.getId());
        CartItem cartItem = cartItem(cart.getId(), course.getId());
        cartItem.setClassId(classOffering.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByIdAndUserId(cart.getId(), user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())).thenReturn(List.of(cartItem));
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
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
        when(sePayInstructionServices.getIfAvailable()).thenReturn(request ->
                new SePayPaymentInstruction(
                        "SLPCLASS1",
                        "123456789",
                        "MBBANK",
                        "SMART LEARNLY",
                        "https://qr.example/SLPCLASS1",
                        request.amount(),
                        request.expiresAt()
                ));
        when(sePayOrderRepository.save(any(SePayOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = service.checkout(cart.getId());

        assertThat(response.amount()).isEqualByComparingTo("1500000");
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue().get(0).getUnitPrice()).isEqualByComparingTo("1500000");
        assertThat(itemsCaptor.getValue().get(0).getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(itemsCaptor.getValue().get(0).getFinalAmount()).isEqualByComparingTo("1500000");
    }

    @Test
    void checkoutShouldRollbackBeforePersistingSePayOrderWhenAdapterMissing() {
        UserAccount user = user();
        Cart cart = cart(user.getId());
        Course course = paidPublishedCourse();
        CartItem cartItem = cartItem(cart.getId(), course.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByIdAndUserId(cart.getId(), user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())).thenReturn(List.of(cartItem));
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(sePayInstructionServices.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.checkout(cart.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

        verify(sePayOrderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByCartId(cart.getId());
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole("TRAINEE");
        return user;
    }

    private Cart cart(UUID userId) {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUserId(userId);
        return cart;
    }

    private CartItem cartItem(UUID cartId, UUID courseId) {
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setCartId(cartId);
        item.setCourseId(courseId);
        return item;
    }

    private Course paidPublishedCourse() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Java Backend");
        course.setPrice(new BigDecimal("399000"));
        course.setFree(false);
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }

    private ClassOffering paidClass(UUID courseId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(courseId);
        classOffering.setClassName("Java Backend - K01");
        classOffering.setStatus(ClassStatus.UPCOMING);
        return classOffering;
    }
}
