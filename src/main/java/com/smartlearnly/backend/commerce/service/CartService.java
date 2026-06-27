package com.smartlearnly.backend.commerce.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.dto.AddCartItemRequest;
import com.smartlearnly.backend.commerce.dto.CartItemResponse;
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
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CourseRepository courseRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        UserAccount user = currentUserService.requireAuthenticatedUser();
        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        if (cart == null) {
            return new CartResponse(null, List.of());
        }
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        UserAccount user = currentUserService.requireAuthenticatedUser();

        Course course = requirePublishedCourse(request.courseId());
        requirePaidCourse(course);

        ClassOffering classOffering = requireSellableClass(
                request.classId(),
                course.getId());

        rejectExistingCourseAccess(user.getId(), course.getId());
        rejectExistingClassAccess(user.getId(), classOffering.getId());

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user.getId()));

        if (cartItemRepository.existsSameProduct(cart.getId(), course.getId(), request.classId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Cart item already exists");
        }

        CartItem item = new CartItem();
        item.setCartId(cart.getId());
        item.setCourseId(course.getId());
        item.setClassId(classOffering.getId());

        cartItemRepository.save(item);

        return toCartResponse(cart);
    }

    @Transactional
    public void removeItem(UUID itemId) {
        UserAccount user = currentUserService.requireAuthenticatedUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart was not found"));
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item was not found"));
        cartItemRepository.delete(item);
    }

    private Cart createCart(UUID userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cartRepository.save(cart);
    }

    private CartResponse toCartResponse(Cart cart) {
        return new CartResponse(
                cart.getId(),
                cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId())
                        .stream()
                        .map(this::toItemResponse)
                        .toList());
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(item.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
        ClassOffering classOffering = requireCartClass(item.getClassId());
        BigDecimal price = resolveCourseFinalAmount(course);
        return new CartItemResponse(
                item.getId(),
                item.getCourseId(),
                course.getTitle(),
                item.getClassId(),
                classOffering == null ? null : classOffering.getClassName(),
                price,
                item.getAddedAt());
    }

    private Course requirePublishedCourse(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
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
        if (classOffering.getStatus() == ClassStatus.CANCELLED
                || classOffering.getStatus() == ClassStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE);
        }
        return classOffering;
    }

    private ClassOffering requireCartClass(UUID classId) {
        if (classId == null) {
            return null;
        }
        return classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
    }

    private void rejectExistingCourseAccess(UUID studentId, UUID courseId) {
        CourseEnrollment enrollment = courseEnrollmentRepository
                .findByCourseIdAndStudentId(courseId, studentId)
                .orElse(null);
        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Learner already has access to this course");
        }
    }

    private void rejectExistingClassAccess(UUID studentId, UUID classId) {
        ClassEnrollment enrollment = classEnrollmentRepository
                .findByClassIdAndStudentId(classId, studentId)
                .orElse(null);
        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Learner already has access to this class");
        }
    }

    private boolean hasAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED;
    }

    private boolean isFree(Course course) {
        BigDecimal price = money(course.getPrice());
        return Boolean.TRUE.equals(course.getFree()) || price.compareTo(BigDecimal.ZERO) == 0;
    }

    private BigDecimal resolveCourseFinalAmount(Course course) {
        return course.getDiscountedPrice() == null
                ? money(course.getPrice())
                : money(course.getDiscountedPrice());
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
