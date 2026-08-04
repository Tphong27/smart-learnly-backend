package com.smartlearnly.backend.commerce.checkout.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.commerce.checkout.dto.CheckoutItemType;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Kiểm tra yêu cầu checkout và tạo dữ liệu giá cho một khóa học hoặc lớp học. */
@Service
@RequiredArgsConstructor
public class CheckoutItemService {
    private final CourseRepository courseRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;

    /**
     * Xác định sản phẩm mà học viên muốn mua và chuyển nó thành một checkout item hợp lệ.
     * Khóa học phải được xuất bản trước khi kiểm tra riêng luồng online hoặc offline.
     *
     * @return thông tin sản phẩm và giá đã sẵn sàng để tạo đơn hàng
     * @throws BusinessException khi sản phẩm không tồn tại hoặc không đủ điều kiện bán
     */
    public CheckoutItem resolve(UUID studentId, CheckoutItemType itemType, UUID courseId, UUID classId) {
        Course course = requirePublishedCourse(courseId);

        if (itemType == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Checkout item type is required");
        }

        return switch (itemType) {
            case COURSE -> resolveCourse(studentId, course, classId);
            case CLASS -> resolveClass(studentId, course, classId);
        };
    }

    /**
     * Chuẩn bị checkout cho khóa học online có tính phí.
     * Không nhận classId, không cho mua khóa học miễn phí và không cho mua lại
     * khi học viên đã có quyền truy cập. Giá giảm hợp lệ được ưu tiên sử dụng.
     */
    private CheckoutItem resolveCourse(UUID studentId, Course course, UUID classId) {
        if (classId != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Class must not be supplied for an online course checkout"
            );
        }

        if (isFree(course)) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_ENROLLABLE,
                    "Free courses must use the free enrollment flow"
            );
        }

        rejectExistingCourseAccess(studentId, course.getId());

        BigDecimal unitPrice = money(course.getPrice());
        BigDecimal finalAmount = resolveFinalAmount(course);

        return new CheckoutItem(
                course.getId(),
                null,
                course.getTitle(),
                unitPrice,
                unitPrice.subtract(finalAmount),
                finalAmount
        );
    }

    /**
     * Chuẩn bị checkout cho một lớp học offline còn mở đăng ký.
     * Lớp phải thuộc đúng khóa học, còn chỗ, có giá dương và học viên chưa sở hữu lớp.
     */
    private CheckoutItem resolveClass(UUID studentId, Course course, UUID classId) {
        if (classId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Class is required for an offline class checkout"
            );
        }

        ClassOffering classOffering = requireSellableClass(classId, course.getId());
        rejectExistingClassAccess(studentId, classOffering.getId());

        BigDecimal classPrice = classOffering.getPrice();
        if (classPrice == null || classPrice.signum() <= 0) {
            throw new BusinessException(
                    ErrorCode.CLASS_NOT_AVAILABLE,
                    "Class does not have a valid registration price"
            );
        }

        BigDecimal finalAmount = money(classPrice);
        return new CheckoutItem(
                course.getId(),
                classOffering.getId(),
                course.getTitle() + " - " + classOffering.getClassName(),
                finalAmount,
                BigDecimal.ZERO,
                finalAmount
        );
    }

    /** Tải khóa học và chỉ chấp nhận khóa đang ở trạng thái PUBLISHED. */
    private Course requirePublishedCourse(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Course was not found"
                ));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_ENROLLABLE);
        }
        return course;
    }

    /**
     * Tải lớp học và kiểm tra quan hệ với khóa học, trạng thái mở bán, ngày bắt đầu,
     * giá và số chỗ còn lại.
     */
    private ClassOffering requireSellableClass(UUID classId, UUID courseId) {
        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class was not found"
                ));

        if (!courseId.equals(classOffering.getCourseId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Class must belong to the selected course");
        }
        if (classOffering.getStatus() != ClassStatus.UPCOMING) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Only upcoming classes can be registered");
        }
        if (classOffering.getStartDate() == null || classOffering.getStartDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Class registration is no longer available");
        }
        if (classOffering.getPrice() == null || classOffering.getPrice().signum() < 0) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Class price is not configured");
        }

        long activeCount = classEnrollmentRepository.countByClassIdAndStatus(classOffering.getId(), "active");
        if (activeCount >= classOffering.getMaxStudents()) {
            throw new BusinessException(ErrorCode.CLASS_FULL);
        }
        return classOffering;
    }

    /** Ngăn học viên tạo đơn mới khi đã có quyền học khóa online này. */
    private void rejectExistingCourseAccess(UUID studentId, UUID courseId) {
        CourseEnrollment enrollment = courseEnrollmentRepository
                .findByCourseIdAndStudentId(courseId, studentId)
                .orElse(null);

        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Learner already has access to this course");
        }
    }

    /** Ngăn học viên tạo đơn mới khi đã có quyền tham gia lớp offline này. */
    private void rejectExistingClassAccess(UUID studentId, UUID classId) {
        ClassEnrollment enrollment = classEnrollmentRepository
                .findByClassIdAndStudentId(classId, studentId)
                .orElse(null);

        if (hasAccess(enrollment == null ? null : enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Learner already has access to this class");
        }
    }

    /** Cho biết enrollment hiện tại đã cấp quyền truy cập hay chưa. */
    private boolean hasAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED;
    }

    /**
     * Chọn giá cuối cùng của khóa học và từ chối giá giảm lớn hơn giá niêm yết.
     */
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
                    "Discounted price cannot exceed course price"
            );
        }
        return finalAmount;
    }

    /** Xác định khóa học thuộc luồng miễn phí thay vì luồng thanh toán. */
    private boolean isFree(Course course) {
        BigDecimal price = money(course.getPrice());
        BigDecimal discountedPrice = course.getDiscountedPrice();
        return Boolean.TRUE.equals(course.getFree())
                || price.signum() == 0
                || (discountedPrice != null && discountedPrice.signum() == 0);
    }

    /** Chuyển giá trị tiền null thành 0 để các phép tính không gặp lỗi. */
    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record CheckoutItem(
            UUID courseId,
            UUID classId,
            String title,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal finalAmount
    ) {
    }
}
