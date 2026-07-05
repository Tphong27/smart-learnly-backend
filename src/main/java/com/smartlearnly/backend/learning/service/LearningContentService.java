package com.smartlearnly.backend.learning.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.dto.*;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.common.exception.ErrorCode;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningContentService {
        private final CourseRepository courseRepository;
        private final CourseSectionRepository courseSectionRepository;
        private final EnrollmentAccessService enrollmentAccessService;
        private final CurrentUserService currentUserService;
        private final LessonProgressRepository lessonProgressRepository;
        private final HlsLessonRepository hlsLessonRepository;
        private final ClassOfferingRepository classOfferingRepository;
        private final ClassEnrollmentRepository classEnrollmentRepository;

        @Transactional(readOnly = true)
        public LearningContentResponse getLearningContent(UUID courseId, UUID classId) {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                enrollmentAccessService.requireCourseAccess(courseId);
                validateClassAccess(student.getId(), courseId, classId);

                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course not found"));

                List<CourseSection> sections = courseSectionRepository
                                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

                Set<UUID> completedLessonIds = lessonProgressRepository
                                .findByStudentIdAndCourseId(student.getId(), courseId)
                                .stream()
                                .filter(LessonProgress::isCompleted)
                                .map(LessonProgress::getLessonId)
                                .collect(Collectors.toSet());

                List<LearningSectionResponse> sectionResponses = sections.stream()
                                .map(section -> toSectionResponse(section, completedLessonIds))
                                .filter(this::hasLessons)
                                .toList();

                LearningStats stats = calculateStats(sectionResponses);

                return new LearningContentResponse(
                                courseId,
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                sectionResponses,
                                stats);
        }

        @Transactional(readOnly = true)
        public LearningContentResponse getPreviewContent(UUID courseId) {
                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new RuntimeException("Course not found"));

                List<CourseSection> sections = courseSectionRepository
                                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

                List<LearningSectionResponse> sectionResponses = sections.stream()
                                .map(this::toPreviewSectionResponse)
                                .filter(s -> s != null)
                                .toList();

                LearningStats stats = calculateStats(sectionResponses);

                return new LearningContentResponse(
                                courseId,
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                sectionResponses,
                                stats);
        }

        @Transactional(readOnly = true)
        public LearningContentResponse getAdminPreviewContent(UUID courseId) {
                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new RuntimeException("Course not found"));

                List<CourseSection> sections = courseSectionRepository
                                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

                List<LearningSectionResponse> sectionResponses = sections.stream()
                                .map(this::toSectionResponseWithoutProgress)
                                .filter(this::hasLessons)
                                .toList();

                LearningStats stats = calculateStats(sectionResponses);

                return new LearningContentResponse(
                                courseId,
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                sectionResponses,
                                stats);
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

        private LearningSectionResponse toSectionResponseWithoutProgress(CourseSection section) {
                List<LearningLessonResponse> lessonResponses = orderedLessons(section).stream()
                                .filter(this::isPublishedLesson)
                                .map(lesson -> toLessonResponse(lesson, false))
                                .toList();

                return new LearningSectionResponse(
                                section.getId(),
                                section.getTitle(),
                                section.getSortOrder(),
                                lessonResponses);
        }

        private LearningSectionResponse toPreviewSectionResponse(CourseSection section) {
                List<LearningLessonResponse> lessonResponses = orderedLessons(section).stream()
                                .filter(this::isPublishedLesson)
                                .filter(lesson -> Boolean.TRUE.equals(lesson.getPreview()))
                                .map(lesson -> toLessonResponse(lesson, false))
                                .toList();
                if (lessonResponses.isEmpty()) {
                        return null;
                }
                return new LearningSectionResponse(
                                section.getId(),
                                section.getTitle(),
                                section.getSortOrder(),
                                lessonResponses);
        }

        private LearningSectionResponse toSectionResponse(
                        CourseSection section,
                        Set<UUID> completedLessonIds) {
                List<LearningLessonResponse> lessonResponses = orderedLessons(section).stream()
                                .filter(this::isPublishedLesson)
                                .map(lesson -> toLessonResponse(
                                                lesson,
                                                completedLessonIds.contains(lesson.getId())))
                                .toList();

                return new LearningSectionResponse(
                                section.getId(),
                                section.getTitle(),
                                section.getSortOrder(),
                                lessonResponses);
        }

        private List<Lesson> orderedLessons(CourseSection section) {
                return section.getLessons().stream()
                                .sorted(Comparator
                                                .comparing(Lesson::getSortOrder,
                                                                Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(Lesson::getCreatedAt,
                                                                Comparator.nullsLast(Instant::compareTo))
                                                .thenComparing(Lesson::getId, Comparator.nullsLast(UUID::compareTo)))
                                .toList();
        }

        private LearningLessonResponse toLessonResponse(Lesson lesson, boolean completed) {
                List<LearningResourceResponse> resourceResponses = lesson.getResources().stream()
                                .map(r -> new LearningResourceResponse(r.getUrl(), r.getName(), r.getContentType()))
                                .toList();

                // Check if HLS content is available
                boolean hlsReady = false;
                String hlsPlaylistUrl = null;

                Optional<HlsLesson> hlsLesson = hlsLessonRepository.findByLessonId(lesson.getId());
                if (hlsLesson.isPresent() && hlsLesson.get().isReady()) {
                        hlsReady = true;
                        // The actual playlist URL is generated with a token, so we just indicate HLS is
                        // available
                        hlsPlaylistUrl = "/api/v1/hls/playlist/" + lesson.getId();
                }

                return new LearningLessonResponse(
                                lesson.getId(),
                                lesson.getTitle(),
                                lesson.getType().name(),
                                lessonStatus(lesson),
                                lesson.getVideoUrl(),
                                lesson.getContent(),
                                lesson.getAttachmentUrl(),
                                lesson.getDurationSeconds(),
                                Boolean.TRUE.equals(lesson.getPreview()),
                                lesson.getSortOrder(),
                                completed,
                                resourceResponses,
                                hlsReady,
                                hlsPlaylistUrl);
        }

        private boolean isPublishedLesson(Lesson lesson) {
                return lesson.getStatus() == LessonStatus.PUBLISHED;
        }

        private boolean hasLessons(LearningSectionResponse section) {
                return !section.lessons().isEmpty();
        }

        private String lessonStatus(Lesson lesson) {
                return lesson.getStatus() == null
                                ? null
                                : lesson.getStatus().name().toLowerCase(Locale.ROOT);
        }

        private LearningStats calculateStats(List<LearningSectionResponse> sections) {
                int totalVideos = 0;
                int totalDocuments = 0;
                int totalQuizzes = 0;
                int totalDurationSeconds = 0;
                int totalLessons = 0;

                for (LearningSectionResponse section : sections) {
                        for (LearningLessonResponse lesson : section.lessons()) {
                                totalLessons++;
                                String type = lesson.lessonType();
                                if ("VIDEO".equals(type)) {
                                        totalVideos++;
                                } else if ("PDF".equals(type)) {
                                        totalDocuments++;
                                } else if ("QUIZ".equals(type)) {
                                        totalQuizzes++;
                                }
                                if (lesson.durationSeconds() != null) {
                                        totalDurationSeconds += lesson.durationSeconds();
                                }
                        }
                }

                return new LearningStats(
                                sections.size(),
                                totalLessons,
                                totalVideos,
                                totalDocuments,
                                totalQuizzes,
                                totalDurationSeconds);
        }
}
