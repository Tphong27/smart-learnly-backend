package com.smartlearnly.backend.course.preview.service;

import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.definition.service.AssignmentService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.preview.dto.PreviewAssignmentResponse;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreviewAssignmentService {
    private final CourseRepository courseRepository;
    private final CurriculumResolutionService curriculumResolutionService;
    private final ClassCurriculumCompositionService compositionService;
    private final AssignmentService assignmentService;

    /** Trả assignment chỉ đọc khi lesson thực sự được công khai trong curriculum preview. */
    @Transactional(readOnly = true)
    public PreviewAssignmentResponse getPreviewAssignment(
            UUID courseId,
            UUID classId,
            UUID lessonId) {
        if (courseId == null || lessonId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Course ID and lesson ID are required");
        }

        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(this::previewAssignmentNotFound);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw previewAssignmentNotFound();
        }

        CurriculumResolution resolution = classId == null
                ? curriculumResolutionService.resolvePublicMaster(courseId)
                : curriculumResolutionService.resolveClassEffectivePublished(courseId, classId);

        CurriculumLesson lesson = resolution.version()
                .getSections()
                .stream()
                .flatMap(section -> effectiveLessons(section).stream())
                .filter(candidate -> lessonMatches(candidate, lessonId))
                .findFirst()
                .orElseThrow(this::previewAssignmentNotFound);

        boolean assignmentLesson = lesson.getType() == LessonType.ASSIGNMENT
                || lesson.getType() == LessonType.ESSAY;
        if (lesson.getStatus() != LessonStatus.PUBLISHED
                || !Boolean.TRUE.equals(lesson.getPreview())
                || !assignmentLesson) {
            throw previewAssignmentNotFound();
        }

        AssignmentModel.Response assignment = assignmentService
                .findAssignmentByLessonId(lessonId, classId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsArchived()))
                .filter(candidate -> courseId.equals(candidate.getCourseId()))
                .filter(candidate -> candidate.getClassId() == null
                        || candidate.getClassId().equals(classId))
                .orElseThrow(this::previewAssignmentNotFound);

        return new PreviewAssignmentResponse(
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getRubric(),
                assignment.getInstructionFileUrl(),
                assignment.getInstructionFileName(),
                assignment.getDueDate(),
                assignment.getAllowLateSubmission(),
                assignment.getLockoutDate(),
                assignment.getMaxScore());
    }

    /** Tôn trọng lesson override khi preview curriculum của một class. */
    private List<CurriculumLesson> effectiveLessons(CurriculumSection section) {
        if (compositionService.isCompositionVersion(section.getCurriculumVersion())) {
            return compositionService.effectiveLessons(section);
        }
        return section.getLessons();
    }

    /** Nhận cả ID vật lý lẫn ID nguồn/identity của lesson trong curriculum kế thừa. */
    private boolean lessonMatches(CurriculumLesson lesson, UUID lessonId) {
        return Objects.equals(lesson.getId(), lessonId)
                || Objects.equals(lesson.getLessonIdentityId(), lessonId)
                || Objects.equals(lesson.getSourceCurriculumLessonId(), lessonId)
                || Objects.equals(lesson.getSourceLessonId(), lessonId);
    }

    /** Dùng cùng một lỗi 404 để không làm lộ course, lesson hoặc assignment riêng tư. */
    private BusinessException previewAssignmentNotFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Preview assignment was not found");
    }
}
