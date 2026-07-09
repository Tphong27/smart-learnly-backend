package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResourceRequest;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseContentAdminService {
    private static final int MAX_RESOURCES_PER_LESSON = 10;

    private final CourseRepository courseRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CurriculumSectionRepository sectionRepository;
    private final CurriculumLessonRepository lessonRepository;
    private final CurriculumDtoMapper curriculumDtoMapper;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final QuizContentValidator quizContentValidator;

    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(UUID courseId) {
        CurriculumVersion version = findMasterAuthoringVersion(courseId);
        return orderedSections(version).stream()
                .map(curriculumDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse getSection(UUID sectionId) {
        return curriculumDtoMapper.toSectionResponse(findSection(sectionId));
    }

    @Transactional
    public SectionResponse createSection(UUID courseId, SectionRequest request) {
        CurriculumVersion version = findOrCreateMasterAuthoringVersion(courseId);
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        section.setSortOrder(request.sortOrder() == null
                ? sectionRepository.findMaxSortOrderByCurriculumVersionId(version.getId()) + 1
                : request.sortOrder());

        CurriculumSection saved = sectionRepository.save(section);
        audit("SECTION_CREATED", "CURRICULUM_SECTION", saved.getId());
        return curriculumDtoMapper.toSectionResponse(saved);
    }

    @Transactional
    public SectionResponse updateSection(UUID sectionId, SectionRequest request) {
        CurriculumSection section = findSection(sectionId);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }

        CurriculumSection saved = sectionRepository.save(section);
        audit("SECTION_UPDATED", "CURRICULUM_SECTION", saved.getId());
        return curriculumDtoMapper.toSectionResponse(saved);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        CurriculumSection section = findSection(sectionId);
        sectionRepository.delete(section);
        audit("SECTION_DELETED", "CURRICULUM_SECTION", section.getId());
    }

    @Transactional
    public List<SectionResponse> reorderSections(UUID courseId, ReorderRequest request) {
        CurriculumVersion version = findMasterAuthoringVersion(courseId);
        List<CurriculumSection> sections = sectionRepository
                .findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(version.getId());
        Map<UUID, CurriculumSection> sectionsById = sections.stream()
                .collect(LinkedHashMap::new, (map, section) -> map.put(section.getId(), section), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), sectionsById.keySet(), "Section");

        int sortOrder = 0;
        for (UUID sectionId : request.ids()) {
            sectionsById.get(sectionId).setSortOrder(sortOrder++);
        }

        List<CurriculumSection> saved = sectionRepository.saveAll(sections);
        audit("SECTIONS_REORDERED", "CURRICULUM_VERSION", version.getId());
        return saved.stream()
                .sorted(Comparator.comparing(CurriculumSection::getSortOrder))
                .map(curriculumDtoMapper::toSectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(UUID sectionId) {
        CurriculumSection section = findSection(sectionId);
        return lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId())
                .stream()
                .map(curriculumDtoMapper::toLessonResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {
        return curriculumDtoMapper.toLessonResponse(findLesson(lessonId));
    }

    @Transactional
    public LessonResponse createLesson(UUID sectionId, LessonRequest request) {
        CurriculumSection section = findSection(sectionId);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        applyLessonRequest(lesson, request, true);
        lesson.setSortOrder(request.sortOrder() == null
                ? lessonRepository.findMaxSortOrderBySectionId(sectionId) + 1
                : request.sortOrder());

        CurriculumLesson saved = lessonRepository.save(lesson);
        audit("LESSON_CREATED", "CURRICULUM_LESSON", saved.getId());
        return curriculumDtoMapper.toLessonResponse(saved);
    }

    @Transactional
    public LessonResponse updateLesson(UUID lessonId, LessonRequest request) {
        CurriculumLesson lesson = findLesson(lessonId);
        applyLessonRequest(lesson, request, false);
        if (request.sortOrder() != null) {
            lesson.setSortOrder(request.sortOrder());
        }

        CurriculumLesson saved = lessonRepository.save(lesson);
        audit("LESSON_UPDATED", "CURRICULUM_LESSON", saved.getId());
        return curriculumDtoMapper.toLessonResponse(saved);
    }

    @Transactional
    public void deleteLesson(UUID lessonId) {
        CurriculumLesson lesson = findLesson(lessonId);
        lesson.setStatus(LessonStatus.INACTIVE);
        lessonRepository.save(lesson);
        audit("LESSON_DEACTIVATED", "CURRICULUM_LESSON", lesson.getId());
    }

    @Transactional
    public List<LessonResponse> reorderLessons(UUID sectionId, ReorderRequest request) {
        findSection(sectionId);
        List<CurriculumLesson> lessons = lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(sectionId);
        Map<UUID, CurriculumLesson> lessonsById = lessons.stream()
                .collect(LinkedHashMap::new, (map, lesson) -> map.put(lesson.getId(), lesson), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), lessonsById.keySet(), "Lesson");

        int sortOrder = 0;
        for (UUID lessonId : request.ids()) {
            lessonsById.get(lessonId).setSortOrder(sortOrder++);
        }

        List<CurriculumLesson> saved = lessonRepository.saveAll(lessons);
        audit("LESSONS_REORDERED", "CURRICULUM_SECTION", sectionId);
        return saved.stream()
                .sorted(Comparator.comparing(CurriculumLesson::getSortOrder))
                .map(curriculumDtoMapper::toLessonResponse)
                .toList();
    }

    private void applyLessonRequest(CurriculumLesson lesson, LessonRequest request, boolean create) {
        lesson.setTitle(normalizeRequired(request.title(), "Lesson title is required"));
        lesson.setType(parseLessonType(resolveLessonType(request), create ? LessonType.RICH_TEXT : lesson.getType()));
        lesson.setVideoUrl(normalizeNullable(request.videoUrl()));
        String content = normalizeNullable(request.content());
        if (lesson.getType() == LessonType.QUIZ) {
            quizContentValidator.validate(content);
        }
        lesson.setContent(content);
        lesson.setAttachmentUrl(normalizeNullable(request.attachmentUrl()));
        lesson.setDurationSeconds(request.durationSeconds());
        if (create || request.isPreview() != null) {
            lesson.setPreview(Boolean.TRUE.equals(request.isPreview()));
        }
        lesson.setStatus(parseLessonStatus(request.status(), create ? LessonStatus.DRAFT : lesson.getStatus()));
        if (request.resources() != null) {
            if (request.resources().size() > MAX_RESOURCES_PER_LESSON) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
            }
            lesson.getResources().clear();
            IntStream.range(0, request.resources().size())
                    .mapToObj(index -> toLessonResource(request.resources().get(index), index))
                    .forEach(lesson::addResource);
        }
    }

    private CurriculumVersion findMasterAuthoringVersion(UUID courseId) {
        findCourse(courseId);
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(courseId, CurriculumScope.MASTER)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Master curriculum version was not found"));
    }

    private CurriculumVersion findOrCreateMasterAuthoringVersion(UUID courseId) {
        Course course = findCourse(courseId);
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(courseId, CurriculumScope.MASTER)
                .orElseGet(() -> createInitialMasterDraft(course));
    }

    private CurriculumVersion createInitialMasterDraft(Course course) {
        CurriculumVersion version = new CurriculumVersion();
        version.setCourseId(course.getId());
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.DRAFT);
        version.setVersionNumber(curriculumVersionRepository.findMaxMasterVersionNumber(course.getId(), CurriculumScope.MASTER) + 1);
        version.setTitle(course.getTitle());
        version.setCreatedBy(currentUserService.requireAuthenticatedUser().getId());
        return curriculumVersionRepository.save(version);
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private CurriculumSection findSection(UUID sectionId) {
        CurriculumSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
        if (section.getCurriculumVersion() == null
                || section.getCurriculumVersion().getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }
        findCourse(section.getCurriculumVersion().getCourseId());
        return section;
    }

    private CurriculumLesson findLesson(UUID lessonId) {
        CurriculumLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        CurriculumVersion version = lesson.getSection().getCurriculumVersion();
        if (version == null || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        findCourse(version.getCourseId());
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
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("DOCUMENT".equals(normalized)) {
            return LessonType.PDF;
        }
        try {
            return LessonType.valueOf(normalized);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson type must be video, pdf, document, rich_text, quiz, flashcard, assignment, or essay"
            );
        }
    }

    private String resolveLessonType(LessonRequest request) {
        String lessonType = normalizeNullable(request.lessonType());
        return lessonType == null ? normalizeNullable(request.type()) : lessonType;
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

    private CurriculumLessonResource toLessonResource(LessonResourceRequest request, int index) {
        CurriculumLessonResource resource = new CurriculumLessonResource();
        String url = normalizeRequired(request.url(), "Resource URL is required");
        resource.setUrl(url);
        resource.setObjectPath(normalizeNullable(request.objectPath()));
        resource.setName(resolveResourceName(request, url, index));
        resource.setFileSize(request.fileSize());
        resource.setContentType(normalizeNullable(request.contentType()));
        resource.setSortOrder(request.sortOrder() == null ? index : request.sortOrder());
        return resource;
    }

    private String resolveResourceName(LessonResourceRequest request, String url, int index) {
        String name = normalizeNullable(request.name());
        if (name == null) {
            name = normalizeNullable(request.fileName());
        }
        if (name == null) {
            name = fileNameFromUrl(url);
        }
        if (name == null) {
            name = "resource-" + (index + 1);
        }
        if (name.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Resource name must not exceed 255 characters");
        }
        return name;
    }

    private String fileNameFromUrl(String url) {
        int fragmentIndex = url.indexOf('#');
        String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
        int queryIndex = withoutFragment.indexOf('?');
        String withoutQuery = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
        int slashIndex = withoutQuery.lastIndexOf('/');
        String fileName = slashIndex < 0 ? withoutQuery : withoutQuery.substring(slashIndex + 1);
        return normalizeNullable(fileName);
    }

    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }
}
