package com.smartlearnly.backend.learning.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
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
import com.smartlearnly.backend.course.service.CourseAccessService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
        private final CurriculumResolutionService curriculumResolutionService;
        private final CurriculumDtoMapper curriculumDtoMapper;
        private final CourseAccessService courseAccessService;

        @Transactional(readOnly = true)
        public LearningContentResponse getLearningContent(UUID courseId, UUID classId) {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                enrollmentAccessService.requireCourseAccess(courseId);
                CurriculumResolution resolution = curriculumResolutionService
                                .resolveTraineeProgress(courseId, classId, student.getId());

                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course not found"));

                Set<UUID> completedLessonIdentityIds = lessonProgressRepository
                                .findByStudentIdAndClassIdAndCourseId(student.getId(), classId, courseId)
                                .stream()
                                .filter(LessonProgress::isCompleted)
                                .map(LessonProgress::getLessonIdentityId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());

                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());

                return curriculumDtoMapper.toLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                completedLessonIdentityIds,
                                metadata);
        }

        @Transactional(readOnly = true)
        public LearningContentResponse getPreviewContent(UUID courseId) {
                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new RuntimeException("Course not found"));
                CurriculumResolution resolution = curriculumResolutionService.resolvePublicMaster(courseId);
                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());

                return curriculumDtoMapper.toPreviewLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                metadata);
        }

        @Transactional(readOnly = true)
        public LearningContentResponse getAdminPreviewContent(UUID courseId) {
                courseAccessService.requireReadableCourse(courseId);

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                CurriculumResolution resolution = curriculumResolutionService
                                .resolveMasterAuthoring(courseId);

                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());

                return curriculumDtoMapper.toLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                Set.of(),
                                metadata);
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
