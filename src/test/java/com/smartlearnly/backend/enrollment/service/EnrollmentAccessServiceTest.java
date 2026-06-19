package com.smartlearnly.backend.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentAccessServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CurrentUserService currentUserService;

    private EnrollmentAccessService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentAccessService(
                courseRepository,
                courseEnrollmentRepository,
                currentUserService
        );
    }

    @Test
    void activeEnrollmentShouldRetainAccessAfterCourseIsInactiveAndSoftDeleted() {
        UUID studentId = UUID.randomUUID();
        Course course = course();
        course.setStatus(CourseStatus.INACTIVE);
        course.setDeletedAt(Instant.now());
        CourseEnrollment enrollment = enrollment(studentId, course.getId(), EnrollmentStatus.ACTIVE);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), studentId))
                .thenReturn(Optional.of(enrollment));

        assertThat(service.requireCourseAccess(course.getId())).isSameAs(enrollment);
    }

    @Test
    void explicitCourseBlockShouldDenyAccessAndExposeReason() {
        UUID studentId = UUID.randomUUID();
        Course course = course();
        course.setAccessBlockedAt(Instant.now());
        course.setAccessBlockReason("Compliance hold");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.requireCourseAccess(course.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.COURSE_ACCESS_BLOCKED);
                    assertThat(exception.getMessage()).isEqualTo("Compliance hold");
                });
    }

    @Test
    void refundedEnrollmentShouldDenyAccess() {
        UUID studentId = UUID.randomUUID();
        Course course = course();
        CourseEnrollment enrollment =
                enrollment(studentId, course.getId(), EnrollmentStatus.REFUNDED);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(studentId));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findByCourseIdAndStudentId(course.getId(), studentId))
                .thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.requireCourseAccess(course.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENROLLMENT_ACCESS_DENIED));
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }

    private CourseEnrollment enrollment(
            UUID studentId,
            UUID courseId,
            EnrollmentStatus status
    ) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(status);
        return enrollment;
    }

    private UserAccount user(UUID id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        return user;
    }
}
