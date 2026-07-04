package com.smartlearnly.backend.lessonprogress.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.enrollment.dto.MyCourseResponse;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.lessonprogress.dto.CourseProgressItemResponse;
import com.smartlearnly.backend.lessonprogress.dto.LessonProgressResponse;
import com.smartlearnly.backend.lessonprogress.dto.ProgressMetricResponse;
import com.smartlearnly.backend.lessonprogress.dto.TraineeProgressResponse;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraineeProgressService {
        private final CurrentUserService currentUserService;
        private final CourseEnrollmentService courseEnrollmentService;
        private final EnrollmentAccessService enrollmentAccessService;
        private final CourseSectionRepository courseSectionRepository;
        private final LessonRepository lessonRepository;
        private final LessonProgressRepository lessonProgressRepository;
        private final ClassOfferingRepository classOfferingRepository;
        private final ClassEnrollmentRepository classEnrollmentRepository;

        @Transactional(readOnly = true)
        public TraineeProgressResponse getMyProgress() {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                List<MyCourseResponse> myCourses = courseEnrollmentService.getMyCourses();

                List<UUID> courseIds = myCourses.stream()
                                .map(MyCourseResponse::id)
                                .toList();

                Map<UUID, List<LessonProgress>> progressByCourseId = lessonProgressRepository
                                .findByStudentIdAndCourseIdIn(student.getId(), courseIds)
                                .stream()
                                .collect(Collectors.groupingBy(LessonProgress::getCourseId));

                List<CourseProgressItemResponse> courses = myCourses.stream()
                                .map(course -> buildCourseProgress(
                                                student.getId(),
                                                course,
                                                progressByCourseId.getOrDefault(course.id(), List.of())))
                                .toList();

                List<CourseProgressItemResponse> completedCourseItems = courses.stream()
                                .filter(course -> "COMPLETED".equals(course.courseStatus()))
                                .toList();

                List<CourseProgressItemResponse> inProgressCourseItems = courses.stream()
                                .filter(course -> !"COMPLETED".equals(course.courseStatus()))
                                .toList();

                return new TraineeProgressResponse(
                                courses.size(),
                                completedCourseItems.size(),
                                inProgressCourseItems.size(),
                                courses,
                                completedCourseItems,
                                inProgressCourseItems);
        }

        @Transactional
        public LessonProgressResponse updateLessonProgress(UUID lessonId, UUID classId, boolean completed) {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                Lesson lesson = lessonRepository.findById(lessonId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Lesson not found"));

                UUID courseId = lesson.getCourse().getId();

                enrollmentAccessService.requireCourseAccess(courseId);
                validateClassAccess(student.getId(), courseId, classId);

                LessonProgress progress = lessonProgressRepository
                                .findByStudentIdAndLessonId(student.getId(), lessonId)
                                .orElseGet(() -> {
                                        LessonProgress created = new LessonProgress();
                                        created.setStudentId(student.getId());
                                        created.setCourseId(courseId);
                                        created.setLessonId(lessonId);
                                        return created;
                                });

                progress.setCompleted(completed);
                progress.setLastAccessedAt(Instant.now());
                progress.setCompletedAt(completed ? Instant.now() : null);

                LessonProgress saved = lessonProgressRepository.save(progress);

                return new LessonProgressResponse(
                                saved.getLessonId(),
                                saved.getCourseId(),
                                saved.isCompleted(),
                                saved.getCompletedAt(),
                                saved.getLastAccessedAt());
        }

        private CourseProgressItemResponse buildCourseProgress(
                        UUID studentId,
                        MyCourseResponse course,
                        List<LessonProgress> progressItems) {
                List<CourseSection> sections = courseSectionRepository
                                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(course.id());

                List<Lesson> lessons = sections.stream()
                                .flatMap(section -> section.getLessons().stream())
                                .filter(this::isVisibleForLearningProgress)
                                .sorted(Comparator
                                                .comparing((Lesson lesson) -> lesson.getSection().getSortOrder())
                                                .thenComparing(Lesson::getSortOrder)
                                                .thenComparing(Lesson::getCreatedAt))
                                .toList();

                Map<UUID, LessonProgress> progressByLessonId = progressItems.stream()
                                .collect(Collectors.toMap(
                                                LessonProgress::getLessonId,
                                                Function.identity(),
                                                (left, right) -> left));

                int lessonTotal = countByProgressGroup(lessons, ProgressGroup.LESSON);
                int lessonCompleted = countCompletedByProgressGroup(
                                lessons,
                                progressByLessonId,
                                ProgressGroup.LESSON);

                int quizTotal = countByProgressGroup(lessons, ProgressGroup.QUIZ);
                int quizCompleted = countCompletedByProgressGroup(
                                lessons,
                                progressByLessonId,
                                ProgressGroup.QUIZ);

                int flashcardTotal = countByProgressGroup(lessons, ProgressGroup.FLASHCARD);
                int flashcardCompleted = countCompletedByProgressGroup(
                                lessons,
                                progressByLessonId,
                                ProgressGroup.FLASHCARD);

                ProgressMetricResponse lessonMetric = metric("Lesson", lessonCompleted, lessonTotal);
                ProgressMetricResponse quizMetric = metric("Quiz", quizCompleted, quizTotal);
                ProgressMetricResponse flashcardMetric = metric("Flashcard", flashcardCompleted, flashcardTotal);

                int overallPercent = calculateOverallPercent(
                                lessonMetric,
                                quizMetric,
                                flashcardMetric);

                UUID classId = course.enrolledClass() == null
                                ? null
                                : course.enrolledClass().id();

                UUID classEnrollmentId = course.enrolledClass() == null
                                ? null
                                : course.enrolledClass().classEnrollmentId();

                String className = course.enrolledClass() == null
                                ? null
                                : course.enrolledClass().className();

                return new CourseProgressItemResponse(
                                course.id(),
                                course.id(),
                                course.enrollmentId(),

                                classId,
                                classEnrollmentId,
                                className,

                                course.title(),
                                course.category() == null ? "Course" : course.category().name(),
                                course.enrollmentStatus(),
                                overallPercent >= 100 ? "COMPLETED" : "IN_PROGRESS",
                                course.accessAllowed(),
                                course.accessBlockedReason(),
                                course.avatarUrl(),
                                overallPercent,
                                lessonMetric,
                                quizMetric,
                                flashcardMetric);
        }

        private void validateClassAccess(UUID studentId, UUID courseId, UUID classId) {
                ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Class not found"));

                if (!courseId.equals(classOffering.getCourseId())) {
                        throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Class does not belong to this course");
                }

                ClassEnrollment classEnrollment = classEnrollmentRepository
                                .findByClassIdAndStudentId(classId, studentId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.FORBIDDEN,
                                                "You are not enrolled in this class"));

                if (classEnrollment.getStatus() != EnrollmentStatus.ACTIVE
                                && classEnrollment.getStatus() != EnrollmentStatus.COMPLETED) {
                        throw new BusinessException(
                                        ErrorCode.FORBIDDEN,
                                        "Class access is not active");
                }
        }

        private boolean isVisibleForLearningProgress(Lesson lesson) {
                return lesson.getStatus() == LessonStatus.PUBLISHED;
        }

        private int countByProgressGroup(List<Lesson> lessons, ProgressGroup group) {
                return (int) lessons.stream()
                                .filter(lesson -> belongsToProgressGroup(lesson.getType(), group))
                                .count();
        }

        private int countCompletedByProgressGroup(
                        List<Lesson> lessons,
                        Map<UUID, LessonProgress> progressByLessonId,
                        ProgressGroup group) {
                return (int) lessons.stream()
                                .filter(lesson -> belongsToProgressGroup(lesson.getType(), group))
                                .filter(lesson -> {
                                        LessonProgress progress = progressByLessonId.get(lesson.getId());
                                        return progress != null && progress.isCompleted();
                                })
                                .count();
        }

        private boolean belongsToProgressGroup(LessonType type, ProgressGroup group) {
                if (type == null) {
                        return false;
                }

                return switch (group) {
                        case LESSON -> type == LessonType.VIDEO
                                        || type == LessonType.PDF
                                        || type == LessonType.RICH_TEXT
                                        || type == LessonType.ASSIGNMENT;
                        case QUIZ -> type == LessonType.QUIZ;
                        case FLASHCARD -> type == LessonType.FLASHCARD;
                };
        }

        private ProgressMetricResponse metric(String label, int completed, int total) {
                int percent = total <= 0
                                ? 0
                                : Math.min(100, Math.round((completed * 100f) / total));

                return new ProgressMetricResponse(label, completed, total, percent);
        }

        private int calculateOverallPercent(
                        ProgressMetricResponse lesson,
                        ProgressMetricResponse quiz,
                        ProgressMetricResponse flashcard) {
                double weightedSum = 0;
                double totalWeight = 0;

                if (lesson.total() > 0) {
                        weightedSum += lesson.percent() * 0.6;
                        totalWeight += 0.6;
                }

                if (quiz.total() > 0) {
                        weightedSum += quiz.percent() * 0.25;
                        totalWeight += 0.25;
                }

                if (flashcard.total() > 0) {
                        weightedSum += flashcard.percent() * 0.15;
                        totalWeight += 0.15;
                }

                if (totalWeight == 0) {
                        return 0;
                }

                return (int) Math.round(weightedSum / totalWeight);
        }

        private enum ProgressGroup {
                LESSON,
                QUIZ,
                FLASHCARD
        }
}