package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResourceRequest;
import com.smartlearnly.backend.course.dto.LessonResourceResponse;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.dto.ClassCurriculumEditorResponse;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumCustomizationState;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
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
public class TrainerClassCurriculumService {
    private static final int MAX_RESOURCES_PER_LESSON = 10;

    private final ClassOfferingRepository classOfferingRepository;
    private final ClassCurriculumBindingRepository bindingRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CurriculumSectionRepository sectionRepository;
    private final CurriculumLessonRepository lessonRepository;
    private final CurriculumResolutionService resolutionService;
    private final CurriculumCloningService cloningService;
    private final CurriculumDtoMapper mapper;
    private final CurrentUserService currentUserService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final QuizContentValidator quizContentValidator;

    @Transactional(readOnly = true)
    public ClassCurriculumEditorResponse getEditorCurriculum(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        CurriculumResolution resolution = resolutionService.resolveTrainerEditing(
                classOffering.getCourseId(),
                classId,
                trainer.getId()
        );
        return toEditorResponse(classId, classOffering.getCourseId(), resolution.binding(), resolution.version(), resolution.source());
    }

    @Transactional
    public ClassCurriculumEditorResponse initializeDraft(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        ClassCurriculumBinding binding = requireBindingForUpdate(classId, classOffering.getCourseId());
        if (binding.getDraftVersionId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum draft already exists");
        }

        CurriculumVersion source = resolutionService.resolveDraftInitializationSource(
                classOffering.getCourseId(),
                classId,
                trainer.getId()
        );
        CurriculumVersion draft = cloningService.cloneToClassDraft(source, classId, trainer.getId());
        binding.setDraftVersionId(draft.getId());
        binding.setCustomizationState(CurriculumCustomizationState.DRAFT);
        ClassCurriculumBinding savedBinding = bindingRepository.save(binding);

        return toEditorResponse(classId, classOffering.getCourseId(), savedBinding, draft, CurriculumResolutionService.SOURCE_CLASS_DRAFT);
    }

    @Transactional
    public SectionResponse createSection(UUID classId, SectionRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(draft);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        section.setSortOrder(request.sortOrder() == null
                ? sectionRepository.findMaxSortOrderByCurriculumVersionId(draft.getId()) + 1
                : request.sortOrder());
        return mapper.toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse updateSection(UUID classId, UUID sectionId, SectionRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }
        return mapper.toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(UUID classId, UUID sectionId) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        sectionRepository.delete(section);
    }

    @Transactional
    public List<SectionResponse> reorderSections(UUID classId, ReorderRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        List<CurriculumSection> sections = sectionRepository.findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(draft.getId());
        Map<UUID, CurriculumSection> sectionsById = sections.stream()
                .collect(LinkedHashMap::new, (map, section) -> map.put(section.getId(), section), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), sectionsById.keySet(), "Section");

        int sortOrder = 0;
        for (UUID requestedId : request.ids()) {
            sectionsById.get(requestedId).setSortOrder(sortOrder++);
        }

        return sectionRepository.saveAll(sections).stream()
                .sorted(Comparator.comparing(CurriculumSection::getSortOrder))
                .map(mapper::toSectionResponse)
                .toList();
    }

    @Transactional
    public LessonResponse createLesson(UUID classId, UUID sectionId, LessonRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        applyLessonRequest(lesson, request, true);
        lesson.setSortOrder(request.sortOrder() == null
                ? lessonRepository.findMaxSortOrderBySectionId(sectionId) + 1
                : request.sortOrder());
        return mapper.toLessonResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public LessonResponse updateLesson(UUID classId, UUID lessonId, LessonRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        applyLessonRequest(lesson, request, false);
        if (request.sortOrder() != null) {
            lesson.setSortOrder(request.sortOrder());
        }
        return mapper.toLessonResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public void deleteLesson(UUID classId, UUID lessonId) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        lessonRepository.delete(lesson);
    }

    @Transactional
    public List<LessonResponse> reorderLessons(UUID classId, UUID sectionId, ReorderRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        requireDraftSection(sectionId, draft.getId());
        List<CurriculumLesson> lessons = lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(sectionId);
        Map<UUID, CurriculumLesson> lessonsById = lessons.stream()
                .collect(LinkedHashMap::new, (map, lesson) -> map.put(lesson.getId(), lesson), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), lessonsById.keySet(), "Lesson");

        int sortOrder = 0;
        for (UUID requestedId : request.ids()) {
            lessonsById.get(requestedId).setSortOrder(sortOrder++);
        }

        return lessonRepository.saveAll(lessons).stream()
                .sorted(Comparator.comparing(CurriculumLesson::getSortOrder))
                .map(mapper::toLessonResponse)
                .toList();
    }

    @Transactional
    public LessonResourceResponse addResource(UUID classId, UUID lessonId, LessonResourceRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        if (lesson.getResources().size() >= MAX_RESOURCES_PER_LESSON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
        }
        CurriculumLessonResource resource = toLessonResource(request, nextResourceSortOrder(lesson));
        lesson.addResource(resource);
        CurriculumLesson saved = lessonRepository.save(lesson);
        return saved.getResources().stream()
                .filter(savedResource -> savedResource == resource || resource.getId().equals(savedResource.getId()))
                .findFirst()
                .map(mapper::toLessonResourceResponse)
                .orElseGet(() -> mapper.toLessonResourceResponse(resource));
    }

    @Transactional
    public List<LessonResourceResponse> replaceResources(UUID classId, UUID lessonId, List<LessonResourceRequest> requests) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        List<LessonResourceRequest> safeRequests = requests == null ? List.of() : requests;
        if (safeRequests.size() > MAX_RESOURCES_PER_LESSON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
        }
        lesson.getResources().clear();
        IntStream.range(0, safeRequests.size())
                .mapToObj(index -> toLessonResource(safeRequests.get(index), index))
                .forEach(lesson::addResource);
        CurriculumLesson saved = lessonRepository.save(lesson);
        return saved.getResources().stream()
                .sorted(Comparator.comparing(CurriculumLessonResource::getSortOrder))
                .map(mapper::toLessonResourceResponse)
                .toList();
    }

    @Transactional
    public void removeResource(UUID classId, UUID lessonId, UUID resourceId) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        boolean removed = lesson.getResources().removeIf(resource -> resourceId.equals(resource.getId()));
        if (!removed) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Resource was not found");
        }
        lessonRepository.save(lesson);
    }

    @Transactional
    public List<LessonResourceResponse> reorderResources(UUID classId, UUID lessonId, ReorderRequest request) {
        CurriculumVersion draft = requireDraft(classId);
        CurriculumLesson lesson = requireDraftLesson(lessonId, draft.getId());
        Map<UUID, CurriculumLessonResource> resourcesById = lesson.getResources().stream()
                .collect(LinkedHashMap::new, (map, resource) -> map.put(resource.getId(), resource), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), resourcesById.keySet(), "Resource");

        int sortOrder = 0;
        for (UUID requestedId : request.ids()) {
            resourcesById.get(requestedId).setSortOrder(sortOrder++);
        }

        CurriculumLesson saved = lessonRepository.save(lesson);
        return saved.getResources().stream()
                .sorted(Comparator.comparing(CurriculumLessonResource::getSortOrder))
                .map(mapper::toLessonResourceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID classId, UUID lessonId) {
        CurriculumLesson lesson = requireOwnedClassLessonForRead(classId, lessonId);
        return mapper.toLessonResponse(lesson);
    }

    /**
     * Verify the class is owned by the current trainer (or bypassed by admin) and that
     * the lesson belongs to the class DRAFT curriculum. Use this for mutating operations
     * inside a trainer's class curriculum editor.
     */
    @Transactional(readOnly = true)
    public CurriculumLesson requireOwnedClassLessonForWrite(UUID classId, UUID lessonId) {
        CurriculumVersion draft = requireDraft(classId);
        return requireDraftLesson(lessonId, draft.getId());
    }

    /**
     * Verify the class is owned by the current trainer (or bypassed by admin) and that
     * the lesson belongs to either the class DRAFT or the class PUBLISHED curriculum
     * (whichever is active). Use this for read-only lookups.
     */
    @Transactional(readOnly = true)
    public CurriculumLesson requireOwnedClassLessonForRead(UUID classId, UUID lessonId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        CurriculumResolution resolution = resolutionService.resolveTrainerEditing(
                classOffering.getCourseId(),
                classId,
                trainer.getId()
        );
        CurriculumVersion active = resolution.version();
        CurriculumLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (!active.getId().equals(lesson.getCurriculumVersionId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    @Transactional
    public ClassCurriculumEditorResponse publishDraft(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        ClassCurriculumBinding binding = requireBindingForUpdate(classId, classOffering.getCourseId());
        if (binding.getDraftVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Initialize a class curriculum draft first");
        }

        CurriculumVersion draft = curriculumVersionRepository.findByIdForUpdate(binding.getDraftVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class curriculum draft not found"));
        assertDraftVersionForClass(draft, classOffering.getCourseId(), classId);

        draft.setStatus(CurriculumStatus.PUBLISHED);
        draft.setPublishedAt(Instant.now());
        CurriculumVersion published = curriculumVersionRepository.save(draft);

        binding.setPublishedVersionId(published.getId());
        binding.setDraftVersionId(null);
        binding.setCustomizationState(CurriculumCustomizationState.PUBLISHED);
        ClassCurriculumBinding savedBinding = bindingRepository.save(binding);

        return toEditorResponse(classId, classOffering.getCourseId(), savedBinding, published, CurriculumResolutionService.SOURCE_CLASS_PUBLISHED);
    }

    private CurriculumVersion requireDraft(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        CurriculumResolution resolution = resolutionService.resolveTrainerDraft(
                classOffering.getCourseId(),
                classId,
                trainer.getId()
        );
        assertDraftVersionForClass(resolution.version(), classOffering.getCourseId(), classId);
        return resolution.version();
    }

    private ClassOffering requireOwnedClass(UUID classId, UUID trainerId) {
        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));
        if (isAdministrator()) {
            return classOffering;
        }
        if (trainerId == null || !trainerId.equals(classOffering.getTrainerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Trainer is not assigned to this class");
        }
        return classOffering;
    }

    private boolean isAdministrator() {
        return authenticatedUserResolver.resolve()
                .map(user -> user.hasRole("ADMIN") || user.hasRole("TMO"))
                .orElse(false);
    }

    private ClassCurriculumBinding requireBindingForUpdate(UUID classId, UUID courseId) {
        ClassCurriculumBinding binding = bindingRepository.findByClassIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class curriculum binding not found"));
        if (!courseId.equals(binding.getCourseId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum binding is inconsistent");
        }
        return binding;
    }

    private CurriculumSection requireDraftSection(UUID sectionId, UUID draftVersionId) {
        return sectionRepository.findByIdAndCurriculumVersionId(sectionId, draftVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
    }

    private CurriculumLesson requireDraftLesson(UUID lessonId, UUID draftVersionId) {
        CurriculumLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (!draftVersionId.equals(lesson.getCurriculumVersionId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    private void assertDraftVersionForClass(CurriculumVersion version, UUID courseId, UUID classId) {
        if (!courseId.equals(version.getCourseId())
                || !classId.equals(version.getClassId())
                || version.getScope() != CurriculumScope.CLASS
                || version.getStatus() != CurriculumStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class draft curriculum is not editable");
        }
    }

    private ClassCurriculumEditorResponse toEditorResponse(
            UUID classId,
            UUID courseId,
            ClassCurriculumBinding binding,
            CurriculumVersion version,
            String source
    ) {
        boolean hasDraft = binding.getDraftVersionId() != null;
        boolean hasPublished = binding.getPublishedVersionId() != null;
        return new ClassCurriculumEditorResponse(
                classId,
                courseId,
                !hasPublished,
                hasDraft,
                hasPublished,
                mapper.toBindingResponse(binding),
                mapper.toMetadata(version, classId, source),
                mapper.toCurriculumVersionResponse(version)
        );
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

    private LessonType parseLessonType(String value, LessonType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        try {
            return LessonType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson type must be video, pdf, rich_text, quiz, flashcard, assignment, or essay"
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
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson status must be draft, published, or inactive");
        }
    }

    private CurriculumLessonResource toLessonResource(LessonResourceRequest request, int fallbackSortOrder) {
        CurriculumLessonResource resource = new CurriculumLessonResource();
        String url = normalizeRequired(request.url(), "Resource URL is required");
        resource.setUrl(url);
        resource.setObjectPath(normalizeNullable(request.objectPath()));
        resource.setName(resolveResourceName(request, url, fallbackSortOrder));
        resource.setFileSize(request.fileSize());
        resource.setContentType(normalizeNullable(request.contentType()));
        resource.setSortOrder(request.sortOrder() == null ? fallbackSortOrder : request.sortOrder());
        return resource;
    }

    private int nextResourceSortOrder(CurriculumLesson lesson) {
        return lesson.getResources().stream()
                .map(CurriculumLessonResource::getSortOrder)
                .filter(sortOrder -> sortOrder != null)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
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

    private String fileNameFromUrl(String url) {
        int fragmentIndex = url.indexOf('#');
        String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
        int queryIndex = withoutFragment.indexOf('?');
        String withoutQuery = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
        int slashIndex = withoutQuery.lastIndexOf('/');
        return normalizeNullable(slashIndex < 0 ? withoutQuery : withoutQuery.substring(slashIndex + 1));
    }
}
