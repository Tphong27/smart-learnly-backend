package com.smartlearnly.backend.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.AddCartItemRequest;
import com.smartlearnly.backend.commerce.dto.CartResponse;
import com.smartlearnly.backend.commerce.entity.Cart;
import com.smartlearnly.backend.commerce.entity.CartItem;
import com.smartlearnly.backend.commerce.repository.CartItemRepository;
import com.smartlearnly.backend.commerce.repository.CartRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
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

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(
                cartRepository,
                cartItemRepository,
                courseRepository,
                classOfferingRepository,
                courseEnrollmentRepository,
                classEnrollmentRepository,
                currentUserService
        );
    }

    @Test
    void addItemShouldRejectDuplicateProduct() {
        UserAccount user = user();
        Course course = paidPublishedCourse();
        Cart cart = cart(user.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.existsSameProduct(cart.getId(), course.getId(), null)).thenReturn(true);

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(course.getId(), null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addItemShouldRejectFreeCourseBecauseFreeFlowDoesNotUseCart() {
        UserAccount user = user();
        Course course = paidPublishedCourse();
        course.setPrice(BigDecimal.ZERO);
        course.setFree(true);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(course.getId(), null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.COURSE_NOT_ENROLLABLE));

        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void getCartShouldReturnClassPriceInsteadOfCoursePrice() {
        UserAccount user = user();
        Course course = paidPublishedCourse();
        ClassOffering classOffering = paidClass(course.getId());
        Cart cart = cart(user.getId());
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setCartId(cart.getId());
        item.setCourseId(course.getId());
        item.setClassId(classOffering.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())).thenReturn(List.of(item));
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));

        CartResponse response = service.getCart();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).price()).isEqualByComparingTo("1500000");
        assertThat(response.items().get(0).price()).isNotEqualByComparingTo(course.getPrice());
    }

    @Test
    void getCartShouldReturnEmptyResponseWhenCartDoesNotExistYet() {
        UserAccount user = user();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThat(service.getCart().cartId()).isNull();
        assertThat(service.getCart().items()).isEmpty();
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
        classOffering.setPrice(new BigDecimal("1500000"));
        classOffering.setStatus(ClassStatus.UPCOMING);
        return classOffering;
    }
}
