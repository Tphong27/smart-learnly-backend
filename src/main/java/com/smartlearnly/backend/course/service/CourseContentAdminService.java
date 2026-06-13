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
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
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
    private final CourseModuleRepository courseModuleRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public SectionResponse createSection(UUID courseId, SectionRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        Course course = findCourse(courseId);

        CourseModule section = new CourseModule();
        section.setCourse(course);
        section.setTitle(normalizeRequiredText(request.title(), "Section title is required"));
        section.setOrderIndex(request.orderIndex() == null
                ? courseModuleRepository.findMaxOrderIndexByCourseId(courseId) + 1
                : request.orderIndex());
        section.setStatus(normalizeContentStatus(request.status(), CourseModule.STATUS_ACTIVE, "Section"));

        CourseModule savedSection = courseModuleRepository.save(section);
        auditLogService.record(admin.getEmail(), "SECTION_CREATED", "SECTION", savedSection.getId().toString());
        return CourseDtoMapper.toSectionResponse(savedSection);
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(UUID courseId) {
        currentUserService.requireAdmin();
        findCourse(courseId);
        return courseModuleRepository.findByCourse_IdOrderByOrderIndexAscCreatedAtAsc(courseId)
                .stream()
                .map(CourseDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse getSection(UUID sectionId) {
        currentUserService.requireAdmin();
        return CourseDtoMapper.toSectionResponse(findSection(sectionId));
    }

    @Transactional
    public SectionResponse updateSection(UUID sectionId, SectionRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        CourseModule section = findSection(sectionId);
        section.setTitle(normalizeRequiredText(request.title(), "Section title is required"));
        if (request.orderIndex() != null) {
            section.setOrderIndex(request.orderIndex());
        }
        section.setStatus(normalizeContentStatus(request.status(), section.getStatus(), "Section"));

        CourseModule savedSection = courseModuleRepository.save(section);
        auditLogService.record(admin.getEmail(), "SECTION_UPDATED", "SECTION", savedSection.getId().toString());
        return CourseDtoMapper.toSectionResponse(savedSection);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        UserAccount admin = currentUserService.requireAdmin();
        CourseModule section = findSection(sectionId);
        section.setStatus(CourseModule.STATUS_INACTIVE);
        courseModuleRepository.save(section);
        auditLogService.record(admin.getEmail(), "SECTION_DEACTIVATED", "SECTION", section.getId().toString());
    }

    @Transactional
    public List<SectionResponse> reorderSections(UUID courseId, ReorderRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        findCourse(courseId);
        List<CourseModule> activeSections = courseModuleRepository.findByCourse_IdAndStatusOrderByOrderIndexAscCreatedAtAsc(
                courseId,
                CourseModule.STATUS_ACTIVE
        );
        Map<UUID, CourseModule> sectionsById = activeSections.stream()
                .collect(LinkedHashMap::new, (map, section) -> map.put(section.getId(), section), LinkedHashMap::putAll);
        assertReorderMatchesActiveItems(request.ids(), sectionsById.keySet(), "Section");

        int orderIndex = 0;
        for (UUID sectionId : request.ids()) {
            sectionsById.get(sectionId).setOrderIndex(orderIndex);
            orderIndex++;
        }
        List<CourseModule> savedSections = courseModuleRepository.saveAll(activeSections);
        auditLogService.record(admin.getEmail(), "SECTIONS_REORDERED", "COURSE", courseId.toString());
        return savedSections.stream()
                .sorted(java.util.Comparator.comparing(CourseModule::getOrderIndex))
                .map(CourseDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional
    public LessonResponse createLesson(UUID sectionId, LessonRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        CourseModule section = findSection(sectionId);
        if (section.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found");
        }

        Lesson lesson = new Lesson();
        lesson.setModule(section);
        lesson.setTitle(normalizeRequiredText(request.title(), "Lesson title is required"));
        lesson.setContent(normalizeNullable(request.content()));
        lesson.setLessonType(normalizeLessonType(request.lessonType(), Lesson.TYPE_RICH_TEXT));
        lesson.setOrderIndex(request.orderIndex() == null
                ? lessonRepository.findMaxOrderIndexByModuleId(sectionId) + 1
                : request.orderIndex());
        if (request.preview() != null) {
            lesson.setPreview(request.preview());
        }
        lesson.setStatus(normalizeContentStatus(request.status(), Lesson.STATUS_ACTIVE, "Lesson"));

        Lesson savedLesson = lessonRepository.save(lesson);
        auditLogService.record(admin.getEmail(), "LESSON_CREATED", "LESSON", savedLesson.getId().toString());
        return CourseDtoMapper.toLessonResponse(savedLesson);
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(UUID sectionId) {
        currentUserService.requireAdmin();
        findSection(sectionId);
        return lessonRepository.findByModule_IdOrderByOrderIndexAscCreatedAtAsc(sectionId)
                .stream()
                .map(CourseDtoMapper::toLessonResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {
        currentUserService.requireAdmin();
        return CourseDtoMapper.toLessonResponse(findLesson(lessonId));
    }

    @Transactional
    public LessonResponse updateLesson(UUID lessonId, LessonRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        Lesson lesson = findLesson(lessonId);
        lesson.setTitle(normalizeRequiredText(request.title(), "Lesson title is required"));
        lesson.setContent(normalizeNullable(request.content()));
        lesson.setLessonType(normalizeLessonType(request.lessonType(), lesson.getLessonType()));
        if (request.orderIndex() != null) {
            lesson.setOrderIndex(request.orderIndex());
        }
        if (request.preview() != null) {
            lesson.setPreview(request.preview());
        }
        lesson.setStatus(normalizeContentStatus(request.status(), lesson.getStatus(), "Lesson"));

        Lesson savedLesson = lessonRepository.save(lesson);
        auditLogService.record(admin.getEmail(), "LESSON_UPDATED", "LESSON", savedLesson.getId().toString());
        return CourseDtoMapper.toLessonResponse(savedLesson);
    }

    @Transactional
    public void deleteLesson(UUID lessonId) {
        UserAccount admin = currentUserService.requireAdmin();
        Lesson lesson = findLesson(lessonId);
        lesson.setStatus(Lesson.STATUS_INACTIVE);
        lessonRepository.save(lesson);
        auditLogService.record(admin.getEmail(), "LESSON_DEACTIVATED", "LESSON", lesson.getId().toString());
    }

    @Transactional
    public List<LessonResponse> reorderLessons(UUID sectionId, ReorderRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        findSection(sectionId);
        List<Lesson> activeLessons = lessonRepository.findByModule_IdAndStatusOrderByOrderIndexAscCreatedAtAsc(
                sectionId,
                Lesson.STATUS_ACTIVE
        );
        Map<UUID, Lesson> lessonsById = activeLessons.stream()
                .collect(LinkedHashMap::new, (map, lesson) -> map.put(lesson.getId(), lesson), LinkedHashMap::putAll);
        assertReorderMatchesActiveItems(request.ids(), lessonsById.keySet(), "Lesson");

        int orderIndex = 0;
        for (UUID lessonId : request.ids()) {
            lessonsById.get(lessonId).setOrderIndex(orderIndex);
            orderIndex++;
        }
        List<Lesson> savedLessons = lessonRepository.saveAll(activeLessons);
        auditLogService.record(admin.getEmail(), "LESSONS_REORDERED", "SECTION", sectionId.toString());
        return savedLessons.stream()
                .sorted(java.util.Comparator.comparing(Lesson::getOrderIndex))
                .map(CourseDtoMapper::toLessonResponse)
                .toList();
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private CourseModule findSection(UUID sectionId) {
        CourseModule section = courseModuleRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
        if (section.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }
        return section;
    }

    private Lesson findLesson(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (lesson.getModule().getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    private void assertReorderMatchesActiveItems(List<UUID> requestedIds, Set<UUID> activeIds, String itemName) {
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);
        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, itemName + " reorder list contains duplicate ids");
        }
        if (!uniqueRequestedIds.equals(activeIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    itemName + " reorder request must include every active item exactly once"
            );
        }
    }

    private String normalizeContentStatus(String value, String defaultStatus, String itemName) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (CourseModule.STATUS_ACTIVE.equals(normalized) || CourseModule.STATUS_INACTIVE.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, itemName + " status must be active or inactive");
    }

    private String normalizeLessonType(String value, String defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (Lesson.TYPE_RICH_TEXT.equals(normalized)
                || Lesson.TYPE_VIDEO.equals(normalized)
                || Lesson.TYPE_PDF.equals(normalized)
                || Lesson.TYPE_QUIZ.equals(normalized)
                || Lesson.TYPE_ASSIGNMENT.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson type is not supported");
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
