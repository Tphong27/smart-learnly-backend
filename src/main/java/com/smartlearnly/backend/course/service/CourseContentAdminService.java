package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseContentAdminService {
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(UUID courseId) {
        findCourse(courseId);
        return courseSectionRepository.findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId)
                .stream()
                .map(CourseDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse getSection(UUID sectionId) {
        return CourseDtoMapper.toSectionResponse(findSection(sectionId));
    }

    @Transactional
    public SectionResponse createSection(UUID courseId, SectionRequest request) {
        Course course = findCourse(courseId);
        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        section.setSortOrder(request.sortOrder() == null
                ? courseSectionRepository.findMaxSortOrderByCourseId(courseId) + 1
                : request.sortOrder());

        CourseSection saved = courseSectionRepository.save(section);
        audit("SECTION_CREATED", "SECTION", saved.getId());
        return CourseDtoMapper.toSectionResponse(saved);
    }

    @Transactional
    public SectionResponse updateSection(UUID sectionId, SectionRequest request) {
        CourseSection section = findSection(sectionId);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }

        CourseSection saved = courseSectionRepository.save(section);
        audit("SECTION_UPDATED", "SECTION", saved.getId());
        return CourseDtoMapper.toSectionResponse(saved);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        CourseSection section = findSection(sectionId);
        courseSectionRepository.delete(section);
        audit("SECTION_DELETED", "SECTION", section.getId());
    }

    @Transactional
    public List<SectionResponse> reorderSections(UUID courseId, ReorderRequest request) {
        findCourse(courseId);
        List<CourseSection> sections = courseSectionRepository.findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);
        Map<UUID, CourseSection> sectionsById = sections.stream()
                .collect(LinkedHashMap::new, (map, section) -> map.put(section.getId(), section), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), sectionsById.keySet(), "Section");

        int sortOrder = 0;
        for (UUID sectionId : request.ids()) {
            sectionsById.get(sectionId).setSortOrder(sortOrder);
            sortOrder++;
        }

        List<CourseSection> saved = courseSectionRepository.saveAll(sections);
        audit("SECTIONS_REORDERED", "COURSE", courseId);
        return saved.stream()
                .sorted(Comparator.comparing(CourseSection::getSortOrder))
                .map(CourseDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(UUID sectionId) {
        findSection(sectionId);
        return lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(sectionId)
                .stream()
                .map(CourseDtoMapper::toLessonResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {
        return CourseDtoMapper.toLessonResponse(findLesson(lessonId));
    }

    @Transactional
    public LessonResponse createLesson(UUID sectionId, LessonRequest request) {
        CourseSection section = findSection(sectionId);
        Lesson lesson = new Lesson();
        lesson.setCourse(section.getCourse());
        lesson.setSection(section);
        applyLessonRequest(lesson, request, true);
        lesson.setSortOrder(request.sortOrder() == null
                ? lessonRepository.findMaxSortOrderBySectionId(sectionId) + 1
                : request.sortOrder());

        Lesson saved = lessonRepository.save(lesson);
        audit("LESSON_CREATED", "LESSON", saved.getId());
        return CourseDtoMapper.toLessonResponse(saved);
    }

    @Transactional
    public LessonResponse updateLesson(UUID lessonId, LessonRequest request) {
        Lesson lesson = findLesson(lessonId);
        applyLessonRequest(lesson, request, false);
        if (request.sortOrder() != null) {
            lesson.setSortOrder(request.sortOrder());
        }

        Lesson saved = lessonRepository.save(lesson);
        audit("LESSON_UPDATED", "LESSON", saved.getId());
        return CourseDtoMapper.toLessonResponse(saved);
    }

    @Transactional
    public void deleteLesson(UUID lessonId) {
        Lesson lesson = findLesson(lessonId);
        lesson.setStatus(LessonStatus.INACTIVE);
        lessonRepository.save(lesson);
        audit("LESSON_DEACTIVATED", "LESSON", lesson.getId());
    }

    @Transactional
    public List<LessonResponse> reorderLessons(UUID sectionId, ReorderRequest request) {
        findSection(sectionId);
        List<Lesson> lessons = lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(sectionId);
        Map<UUID, Lesson> lessonsById = lessons.stream()
                .collect(LinkedHashMap::new, (map, lesson) -> map.put(lesson.getId(), lesson), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), lessonsById.keySet(), "Lesson");

        int sortOrder = 0;
        for (UUID lessonId : request.ids()) {
            lessonsById.get(lessonId).setSortOrder(sortOrder);
            sortOrder++;
        }

        List<Lesson> saved = lessonRepository.saveAll(lessons);
        audit("LESSONS_REORDERED", "SECTION", sectionId);
        return saved.stream()
                .sorted(Comparator.comparing(Lesson::getSortOrder))
                .map(CourseDtoMapper::toLessonResponse)
                .toList();
    }

    private void applyLessonRequest(Lesson lesson, LessonRequest request, boolean create) {
        lesson.setTitle(normalizeRequired(request.title(), "Lesson title is required"));
        lesson.setType(parseLessonType(request.lessonType(), create ? LessonType.RICH_TEXT : lesson.getType()));
        lesson.setVideoUrl(normalizeNullable(request.videoUrl()));
        lesson.setContent(normalizeNullable(request.content()));
        lesson.setAttachmentUrl(normalizeNullable(request.attachmentUrl()));
        lesson.setDurationSeconds(request.durationSeconds());
        if (create || request.isPreview() != null) {
            lesson.setPreview(Boolean.TRUE.equals(request.isPreview()));
        }
        lesson.setStatus(parseLessonStatus(request.status(), create ? LessonStatus.DRAFT : lesson.getStatus()));
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private CourseSection findSection(UUID sectionId) {
        CourseSection section = courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
        if (section.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }
        return section;
    }

    private Lesson findLesson(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    private void assertReorderMatchesAllItems(List<UUID> requestedIds, Set<UUID> existingIds, String itemName) {
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);
        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, itemName + " reorder list contains duplicate ids");
        }
        if (!uniqueRequestedIds.equals(existingIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    itemName + " reorder request must include every item exactly once"
            );
        }
    }

    private LessonType parseLessonType(String value, LessonType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        try {
            return LessonType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson type must be video, pdf, or rich_text");
        }
    }

    private LessonStatus parseLessonStatus(String value, LessonStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        try {
            return LessonStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson status must be draft, published, or inactive");
        }
    }

    private void audit(String action, String targetType, UUID targetId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.record(actor.getEmail(), action, targetType, targetId.toString());
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
