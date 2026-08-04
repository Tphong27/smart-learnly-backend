package com.smartlearnly.backend.learning.content.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;

import java.util.Objects;
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
        private final CurrentUserService currentUserService;
        private final LessonProgressRepository lessonProgressRepository;
        private final CurriculumResolutionService curriculumResolutionService;
        private final CurriculumDtoMapper curriculumDtoMapper;
        private final CourseAccessService courseAccessService;

        /** Tạo nội dung học thật cho học viên sau khi kiểm tra quyền enrollment và scope lớp học. */
        @Transactional(readOnly = true)
        public LearningContentResponse getLearningContent(UUID courseId, UUID classId) {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                CurriculumResolution resolution;

                /*
                 * Học online:
                 * - Không có classId.
                 * - Kiểm tra CourseEnrollment.
                 * - Sử dụng master curriculum của course.
                 */
                if (classId == null) {
                        resolution = curriculumResolutionService.resolveOnlineLearning(courseId, student.getId());
                }

                /*
                 * Học theo lớp offline:
                 * - Có classId.
                 * - Kiểm tra ClassEnrollment.
                 * - Sử dụng curriculum hiệu lực của class.
                 */
                else {
                        resolution = curriculumResolutionService.resolveClassLearning(courseId, classId,
                                        student.getId());
                }

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course not found"));

                Set<UUID> completedLessonIdentityIds;

                /*
                 * classId null nghĩa là học online.
                 * Tạm thời không gọi query progress theo class với giá trị null.
                 */
                if (classId == null) {
                        completedLessonIdentityIds = lessonProgressRepository
                                        .findByStudentIdAndCourseIdAndClassIdIsNull(student.getId(), courseId)
                                        .stream()
                                        .filter(LessonProgress::isCompleted)
                                        .map(LessonProgress::getLessonIdentityId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                } else {
                        completedLessonIdentityIds = lessonProgressRepository
                                        .findByStudentIdAndClassIdAndCourseId(student.getId(), classId, courseId)
                                        .stream()
                                        .filter(LessonProgress::isCompleted)
                                        .map(LessonProgress::getLessonIdentityId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                }

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

        /** Tạo nội dung xem trước công khai chỉ với curriculum đã xuất bản. */
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

        /** Tạo nội dung xem trước cho nhân sự, đúng với phạm vi course hoặc lớp được chọn. */
        @Transactional(readOnly = true)
        public LearningContentResponse getAdminPreviewContent(UUID courseId, UUID classId) {
                courseAccessService.requireReadableCourse(courseId);

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                CurriculumResolution resolution;
                if (classId != null) {
                        // A class preview must use the same effective published curriculum that
                        // enrolled trainees receive, without requiring an enrollment for staff.
                        resolution = curriculumResolutionService.resolveClassEffectivePublished(courseId, classId);
                } else if (course.getStatus() == CourseStatus.PUBLISHED) {
                        // Keep "View as trainee" truthful for published courses.
                        resolution = curriculumResolutionService.resolvePublicMaster(courseId);
                } else {
                        // Draft courses have no learner-facing published version yet, so retain an
                        // authoring preview and expose that distinction through curriculum.source.
                        resolution = curriculumResolutionService.resolveMasterAuthoring(courseId);
                }

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

}
