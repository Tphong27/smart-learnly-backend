package com.smartlearnly.backend.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.dto.EnrollmentResponse;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import com.smartlearnly.backend.enrollment.entity.EnrollmentTransitionSource;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.EnrollmentStatusHistoryRepository;
import com.smartlearnly.backend.payment.repository.SuccessfulPaymentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private EnrollmentStatusHistoryRepository enrollmentStatusHistoryRepository;
    @Mock
    private SuccessfulPaymentRepository successfulPaymentRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private CourseEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new CourseEnrollmentService(
                courseRepository,
                courseEnrollmentRepository,
                enrollmentStatusHistoryRepository,
                successfulPaymentRepository,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void freeCourseShouldCreateActiveEnrollmentAndAudit() {
        UUID studentId = UUID.randomUUID();
        Course course = publishedCourse(BigDecimal.ZERO, false);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentIdForUpdate(course.getId(), studentId))
                .thenReturn(Optional.empty());
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> {
            CourseEnrollment enrollment = invocation.getArgument(0);
            enrollment.setId(UUID.randomUUID());
            enrollment.setEnrollmentDate(Instant.now());
            return enrollment;
        });

        EnrollmentResponse response = service.enrollFreeCourse(course.getId());

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.alreadyEnrolled()).isFalse();
        assertThat(response.reactivated()).isFalse();
        ArgumentCaptor<EnrollmentStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(EnrollmentStatusHistory.class);
        verify(enrollmentStatusHistoryRepository).save(historyCaptor.capture());
        EnrollmentStatusHistory courseHistory = historyCaptor.getValue();
        assertThat(courseHistory.getSource())
                .isEqualTo(EnrollmentTransitionSource.FREE_ENROLLMENT);
        assertThat(courseHistory.getFromStatus()).isNull();
    }

    @Test
    void activeCourseEnrollmentShouldBeIdempotent() {
        UUID studentId = UUID.randomUUID();
        Course course = publishedCourse(BigDecimal.ZERO, false);
        CourseEnrollment existing = enrollment(studentId, course.getId(), EnrollmentStatus.ACTIVE);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentIdForUpdate(course.getId(), studentId))
                .thenReturn(Optional.of(existing));

        EnrollmentResponse response = service.enrollFreeCourse(course.getId());

        assertThat(response.alreadyEnrolled()).isTrue();
        verify(courseEnrollmentRepository, never()).save(any());
        verify(enrollmentStatusHistoryRepository, never()).save(any());
    }

    @Test
    void cancelledFreeEnrollmentShouldReactivateWithAudit() {
        UUID studentId = UUID.randomUUID();
        Course course = publishedCourse(BigDecimal.ZERO, true);
        CourseEnrollment existing = enrollment(studentId, course.getId(), EnrollmentStatus.CANCELLED);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentIdForUpdate(course.getId(), studentId))
                .thenReturn(Optional.of(existing));
        when(courseEnrollmentRepository.save(existing)).thenReturn(existing);

        EnrollmentResponse response = service.enrollFreeCourse(course.getId());

        assertThat(response.reactivated()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        ArgumentCaptor<EnrollmentStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(EnrollmentStatusHistory.class);
        verify(enrollmentStatusHistoryRepository).save(historyCaptor.capture());
        EnrollmentStatusHistory courseHistory = historyCaptor.getValue();
        assertThat(courseHistory.getFromStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(courseHistory.getSource())
                .isEqualTo(EnrollmentTransitionSource.FREE_ENROLLMENT);
    }

    @Test
    void paidReactivationShouldRequireMatchingSuccessfulPayment() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(successfulPaymentRepository.existsForCourse(transactionId, studentId, courseId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.grantPaidCourseEnrollment(studentId, courseId, transactionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_SUCCESSFUL));

        verify(courseEnrollmentRepository, never())
                .findByCourseIdAndStudentIdForUpdate(any(), any());
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void refundedPaidEnrollmentShouldReactivateOnlyAfterSuccessfulPayment() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Course course = publishedCourse(new BigDecimal("399000"), false);
        CourseEnrollment existing = enrollment(studentId, course.getId(), EnrollmentStatus.REFUNDED);
        when(successfulPaymentRepository.existsForCourse(transactionId, studentId, course.getId()))
                .thenReturn(true);
        when(courseEnrollmentRepository.findByCourseIdAndStudentIdForUpdate(course.getId(), studentId))
                .thenReturn(Optional.of(existing));
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.save(existing)).thenReturn(existing);

        EnrollmentResponse response =
                service.grantPaidCourseEnrollment(studentId, course.getId(), transactionId);

        assertThat(response.reactivated()).isTrue();
        ArgumentCaptor<EnrollmentStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(EnrollmentStatusHistory.class);
        verify(enrollmentStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(EnrollmentStatus.REFUNDED);
        assertThat(historyCaptor.getValue().getTransactionId()).isEqualTo(transactionId);
        assertThat(historyCaptor.getValue().getSource())
                .isEqualTo(EnrollmentTransitionSource.PAYMENT_SUCCESS);
    }

    @Test
    void paidCourseShouldBeRejectedByFreeEnrollment() {
        UUID studentId = UUID.randomUUID();
        Course course = publishedCourse(new BigDecimal("399000"), false);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.enrollFreeCourse(course.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.COURSE_NOT_FREE));
        verify(courseEnrollmentRepository, never()).save(any());
    }

    private Course publishedCourse(BigDecimal price, boolean free) {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setPrice(price);
        course.setFree(free);
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }

    private UserAccount user(UUID id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        return user;
    }

    private CourseEnrollment enrollment(UUID studentId, UUID courseId, EnrollmentStatus status) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(Instant.now());
        return enrollment;
    }

}
