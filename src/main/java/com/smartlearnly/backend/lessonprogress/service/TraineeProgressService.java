package com.smartlearnly.backend.lessonprogress.service;

import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.enrollment.dto.MyCourseResponse;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
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
        private final CourseSectionRepository courseSectionRepository;
        private final LessonRepository lessonRepository;
        private final LessonProgressRepository lessonProgressRepository;
        private final ClassOfferingRepository classOfferingRepository;
        private final CurriculumResolutionService curriculumResolutionService;
        private final CurriculumLessonRepository curriculumLessonRepository;
        private final AssignmentRepository assignmentRepository;
        private final AssignmentSubmissionRepository assignmentSubmissionRepository;

        @Transactional(readOnly = true)
        public TraineeProgressResponse getMyProgress() {
                UserAccount student = currentUserService.requireAuthenticatedUser();
                List<MyCourseResponse> myCourses = courseEnrollmentService.getMyCourses();

                List<CourseProgressItemResponse> courses = myCourses.stream()
                                .filter(course -> course.enrolledClass() != null)
                                .map(course -> buildClassProgress(student.getId(), course))
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
                ClassOffering classOffering = requireClass(classId);
                UUID courseId = classOffering.getCourseId();

                CurriculumResolution resolution = curriculumResolutionService
                                .resolveTraineeLearning(courseId, classId, student.getId());
                CurriculumLesson lesson = curriculumLessonRepository
                                .findEffectiveLessonReference(resolution.version().getId(), lessonId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Lesson not found in this class curriculum"));

                LessonProgress progress = lessonProgressRepository
                                .findByStudentIdAndClassIdAndLessonIdentityId(
                                                student.getId(),
                                                classId,
                                                lesson.getLessonIdentityId())
                                .orElseGet(() -> {
                                        LessonProgress created = new LessonProgress();
                                        created.setStudentId(student.getId());
                                        created.setCourseId(courseId);
                                        created.setClassId(classId);
                                        created.setLessonIdentityId(lesson.getLessonIdentityId());
                                        created.setLessonId(lesson.getId());
                                        return created;
                                });

                progress.setCourseId(courseId);
                progress.setClassId(classId);
                progress.setLessonIdentityId(lesson.getLessonIdentityId());
                progress.setLessonId(lesson.getId());
                progress.setCompleted(completed);
                progress.setLastAccessedAt(Instant.now());
                progress.setCompletedAt(completed ? Instant.now() : null);

                LessonProgress saved = lessonProgressRepository.save(progress);

                return new LessonProgressResponse(
                                saved.getLessonId(),
                                saved.getCourseId(),
                                saved.isCompleted(),
                                saved.getCompletedAt(),
                                saved.getLastAccessedAt(),
                                saved.getClassId(),
                                saved.getLessonIdentityId());
        }

        private CourseProgressItemResponse buildClassProgress(UUID studentId, MyCourseResponse course) {

                if (course.enrolledClass() == null) {
                        throw new BusinessException(ErrorCode.CONFLICT, "Trainee progress requires an enrolled class");
                }

                UUID classId = course.enrolledClass().id();
                UUID classEnrollmentId = course.enrolledClass().classEnrollmentId();
                String className = course.enrolledClass().className();

                ProgressCounts counts = calculateClassCurriculumProgress(studentId, course.id(), classId);
                ProgressMetricResponse lessonMetric = metric("Lesson", counts.lessonCompleted(), counts.lessonTotal());
                ProgressMetricResponse quizMetric = metric("Quiz", counts.quizCompleted(), counts.quizTotal());
                ProgressMetricResponse flashcardMetric = metric("Flashcard", counts.flashcardCompleted(), counts.flashcardTotal());
                ProgressMetricResponse assignmentMetric = calculateAssignmentMetric(studentId, course.id(), classId);
                int overallPercent = calculateOverallPercent(lessonMetric, quizMetric, flashcardMetric);

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
                                flashcardMetric,
                                assignmentMetric);
        }

        private ProgressCounts calculateClassCurriculumProgress(UUID studentId, UUID courseId, UUID classId) {
                CurriculumResolution resolution = curriculumResolutionService
                                .resolveTraineeProgress(courseId, classId, studentId);
                List<CurriculumLesson> lessons = orderedCurriculumLessons(resolution.version()).stream()
                                .filter(this::isVisibleForLearningProgress)
                                .toList();

                Map<UUID, LessonProgress> progressByLessonIdentityId = lessonProgressRepository
                                .findByStudentIdAndClassIdAndCourseId(studentId, classId, courseId)
                                .stream()
                                .filter(progress -> progress.getLessonIdentityId() != null)
                                .collect(Collectors.toMap(
                                                LessonProgress::getLessonIdentityId,
                                                Function.identity(),
                                                (left, right) -> left));

                return new ProgressCounts(
                                countByProgressGroup(lessons, ProgressGroup.LESSON),
                                countCompletedCurriculumByProgressGroup(lessons, progressByLessonIdentityId,
                                                ProgressGroup.LESSON),
                                countByProgressGroup(lessons, ProgressGroup.QUIZ),
                                countCompletedCurriculumByProgressGroup(lessons, progressByLessonIdentityId,
                                                ProgressGroup.QUIZ),
                                countByProgressGroup(lessons, ProgressGroup.FLASHCARD),
                                countCompletedCurriculumByProgressGroup(lessons, progressByLessonIdentityId,
                                                ProgressGroup.FLASHCARD));
        }

        private ProgressMetricResponse calculateAssignmentMetric(UUID studentId, UUID courseId, UUID classId) {
                List<Assignment> assignments = assignmentRepository.findAvailableForStudent(studentId, courseId,
                                classId, false);
                int total = assignments.size();
                int completed = (int) assignments.stream().filter(assignment -> assignmentSubmissionRepository
                                .findByAssignmentIdAndStudentId(assignment.getId(), studentId)
                                .map(AssignmentSubmission::getStatus)
                                .filter(this::isCompletedAssignmentStatus)
                                .isPresent())
                                .count();
                return metric("Assignment", completed, total);
        }

        private boolean isCompletedAssignmentStatus(SubmissionStatus status) {
                return status == SubmissionStatus.SUBMITTED
                                || status == SubmissionStatus.GRADED
                                || status == SubmissionStatus.LATE
                                || status == SubmissionStatus.EXPIRED;
        }

        private ClassOffering requireClass(UUID classId) {
                return classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Class not found"));
        }

        private List<CurriculumLesson> orderedCurriculumLessons(CurriculumVersion version) {
                return version.getSections().stream()
                                .sorted(Comparator
                                                .comparing(CurriculumSection::getSortOrder,
                                                                Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(CurriculumSection::getCreatedAt,
                                                                Comparator.nullsLast(Instant::compareTo)))
                                .flatMap(section -> section.getLessons().stream()
                                                .sorted(Comparator
                                                                .comparing(CurriculumLesson::getSortOrder,
                                                                                Comparator.nullsLast(
                                                                                                Integer::compareTo))
                                                                .thenComparing(CurriculumLesson::getCreatedAt,
                                                                                Comparator.nullsLast(
                                                                                                Instant::compareTo))))
                                .toList();
        }

        private boolean isVisibleForLearningProgress(CurriculumLesson lesson) {
                return lesson.getStatus() == LessonStatus.PUBLISHED;
        }

        private boolean isVisibleForLearningProgress(Lesson lesson) {
                return lesson.getStatus() == LessonStatus.PUBLISHED;
        }

        private int countByProgressGroup(List<CurriculumLesson> lessons, ProgressGroup group) {
                return (int) lessons.stream()
                                .filter(lesson -> belongsToProgressGroup(lesson.getType(), group))
                                .count();
        }

        private int countCompletedCurriculumByProgressGroup(
                        List<CurriculumLesson> lessons,
                        Map<UUID, LessonProgress> progressByLessonIdentityId,
                        ProgressGroup group) {
                return (int) lessons.stream()
                                .filter(lesson -> belongsToProgressGroup(lesson.getType(), group))
                                .filter(lesson -> {
                                        LessonProgress progress = progressByLessonIdentityId
                                                        .get(lesson.getLessonIdentityId());
                                        return progress != null && progress.isCompleted();
                                })
                                .count();
        }

        private int countLegacyByProgressGroup(List<Lesson> lessons, ProgressGroup group) {
                return (int) lessons.stream()
                                .filter(lesson -> belongsToProgressGroup(lesson.getType(), group))
                                .count();
        }

        private int countCompletedLegacyByProgressGroup(
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
                                        || type == LessonType.ASSIGNMENT
                                        || type == LessonType.ESSAY;
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

        private record ProgressCounts(
                        int lessonTotal,
                        int lessonCompleted,
                        int quizTotal,
                        int quizCompleted,
                        int flashcardTotal,
                        int flashcardCompleted) {
        }

        private enum ProgressGroup {
                LESSON,
                QUIZ,
                FLASHCARD
        }
}
