package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsAccessService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final LessonRepository lessonRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;

    /**
     * Checks if a user has access to view a lesson's video.
     * Access is granted if:
     * 1. User is admin
     * 2. User is course instructor
     * 3. User is enrolled in the course
     * 4. Lesson is marked as preview (free)
     */
    @Transactional(readOnly = true)
    public boolean checkUserAccess(UUID userId, UUID lessonId) {
        if (userId == null) {
            log.debug("No userId provided, checking preview access");
            return checkPreviewAccess(lessonId);
        }

        // Get user and lesson
        Optional<UserAccount> userOpt = userRepository.findByIdAndDeletedAtIsNull(userId);
        Optional<Lesson> lessonOpt = lessonRepository.findById(lessonId);

        if (lessonOpt.isEmpty()) {
            log.debug("Lesson not found");
            return false;
        }

        Lesson lesson = lessonOpt.get();
        if (userOpt.isEmpty()) {
            return Boolean.TRUE.equals(lesson.getPreview());
        }

        UserAccount user = userOpt.get();

        // Check admin access
        if (ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            log.debug("Admin access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        // Check instructor access (via course)
        if (checkInstructorAccess(user, lesson.getCourse())) {
            log.debug("Instructor access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        // Check enrollment
        if (checkEnrollment(userId, lesson.getCourse().getId())) {
            log.debug("Enrollment access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        // Check preview access
        if (Boolean.TRUE.equals(lesson.getPreview())) {
            log.debug("Preview access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        log.debug("Access denied for user={}, lesson={}", userId, lessonId);
        return false;
    }

    /**
     * Requires user access or throws exception.
     */
    public void requireUserAccess(UUID userId, UUID lessonId) {
        if (!checkUserAccess(userId, lessonId)) {
            log.warn("Access denied: user={}, lesson={}", userId, lessonId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not have access to this lesson");
        }
    }

    /**
     * Checks if a lesson can be accessed without authentication (preview only).
     */
    @Transactional(readOnly = true)
    public boolean checkPreviewAccess(UUID lessonId) {
        Optional<Lesson> lessonOpt = lessonRepository.findById(lessonId);
        if (lessonOpt.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(lessonOpt.get().getPreview());
    }

    /**
     * Requires preview access or throws exception.
     */
    public void requirePreviewAccess(UUID lessonId) {
        if (!checkPreviewAccess(lessonId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This lesson is not available for preview");
        }
    }

    /**
     * Checks if user is an instructor of the course.
     */
    private boolean checkInstructorAccess(UserAccount user, Course course) {
        return course.getCreator() != null && user.getId().equals(course.getCreator().getId());
    }

    /**
     * Checks if user is enrolled in the course.
     */
    private boolean checkEnrollment(UUID userId, UUID courseId) {
        Optional<CourseEnrollment> enrollment = courseEnrollmentRepository
                .findByCourseIdAndStudentId(courseId, userId);
        return enrollment
                .map(CourseEnrollment::getStatus)
                .filter(status -> status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED)
                .isPresent();
    }

    /**
     * Gets the course associated with a lesson.
     */
    @Transactional(readOnly = true)
    public Optional<Course> getCourseForLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .map(Lesson::getCourse);
    }

}
