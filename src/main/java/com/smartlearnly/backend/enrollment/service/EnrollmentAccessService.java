package com.smartlearnly.backend.enrollment.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentAccessService {
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public CourseEnrollment requireCourseAccess(UUID courseId) {
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Course was not found"
                ));

        if (course.getAccessBlockedAt() != null) {
            String message = course.getAccessBlockReason() == null
                    ? "Course access has been blocked"
                    : course.getAccessBlockReason();
            throw new BusinessException(ErrorCode.COURSE_ACCESS_BLOCKED, message);
        }

        CourseEnrollment enrollment = courseEnrollmentRepository
                .findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_ACCESS_DENIED));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE
                && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ACCESS_DENIED);
        }
        return enrollment;
    }
}
