package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
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
    // Trainee xem video HLS thuộc CurriculumLesson (class draft/published) — cần lookup ở bảng này.
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;

    /** View gọn cho lesson bất kể nguồn (legacy `lessons` hoặc `curriculum_lessons`). */
    private record LessonAccessInfo(UUID courseId, boolean preview) {}

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

        Optional<LessonAccessInfo> lessonOpt = resolveLessonAccess(lessonId);
        if (lessonOpt.isEmpty()) {
            log.debug("Lesson not found");
            return false;
        }
        LessonAccessInfo lesson = lessonOpt.get();

        Optional<UserAccount> userOpt = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (userOpt.isEmpty()) {
            return lesson.preview();
        }

        UserAccount user = userOpt.get();

        // Admin bỏ qua mọi check.
        if (ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            log.debug("Admin access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        Course course = courseRepository.findByIdAndDeletedAtIsNull(lesson.courseId()).orElse(null);
        if (course != null && checkInstructorAccess(user, course)) {
            log.debug("Instructor access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        if (checkEnrollment(userId, lesson.courseId())) {
            log.debug("Enrollment access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        if (lesson.preview()) {
            log.debug("Preview access granted for user={}, lesson={}", userId, lessonId);
            return true;
        }

        log.debug("Access denied for user={}, lesson={}", userId, lessonId);
        return false;
    }

    /**
     * Tra lesson từ cả 2 bảng — trả về info tối thiểu cần cho access check.
     */
    private Optional<LessonAccessInfo> resolveLessonAccess(UUID lessonId) {
        Optional<Lesson> legacy = lessonRepository.findById(lessonId);
        if (legacy.isPresent()) {
            Lesson l = legacy.get();
            UUID courseId = l.getCourse() != null ? l.getCourse().getId() : null;
            return Optional.of(new LessonAccessInfo(courseId, Boolean.TRUE.equals(l.getPreview())));
        }
        Optional<CurriculumLesson> versioned = curriculumLessonRepository.findById(lessonId);
        if (versioned.isPresent()) {
            CurriculumLesson l = versioned.get();
            UUID courseId = l.getSection() != null && l.getSection().getCurriculumVersion() != null
                    ? l.getSection().getCurriculumVersion().getCourseId()
                    : null;
            return Optional.of(new LessonAccessInfo(courseId, Boolean.TRUE.equals(l.getPreview())));
        }
        return Optional.empty();
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
        return resolveLessonAccess(lessonId).map(LessonAccessInfo::preview).orElse(false);
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
        Optional<Course> legacyCourse = lessonRepository.findById(lessonId).map(Lesson::getCourse);
        if (legacyCourse.isPresent()) {
            return legacyCourse;
        }
        return curriculumLessonRepository.findById(lessonId)
                .map(l -> l.getSection() != null && l.getSection().getCurriculumVersion() != null
                        ? l.getSection().getCurriculumVersion().getCourseId()
                        : null)
                .flatMap(id -> id == null ? Optional.empty() : courseRepository.findByIdAndDeletedAtIsNull(id));
    }

}
