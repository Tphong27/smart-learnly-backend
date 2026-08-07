package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.dto.ClassCurriculumEditorResponse;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceResponse;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.curriculum.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import com.smartlearnly.backend.curriculum.entity.CurriculumCustomizationState;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumEntryRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.curriculum.util.CurriculumParseService;
import com.smartlearnly.backend.curriculum.util.CurriculumReorderValidator;
import com.smartlearnly.backend.curriculum.util.CurriculumRequestNormalizer;
import com.smartlearnly.backend.curriculum.util.LessonResourceBuilder;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ClassCurriculumEntryRepository entryRepository;
    private final CurriculumResolutionService resolutionService;
    private final ClassCurriculumBindingProvisioningService bindingProvisioningService;
    private final ClassCurriculumCompositionService compositionService;
    private final CurriculumDtoMapper mapper;
    private final CurrentUserService currentUserService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final QuizContentValidator quizContentValidator;
    private final VideoSummaryService videoSummaryService;
    private final CurriculumLessonTestLinkService lessonTestLinkService;

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

        CurriculumVersion savedDraft = createDraftVersion(classOffering, trainer, source);

        binding.setDraftVersionId(savedDraft.getId());
        binding.setCustomizationState(CurriculumCustomizationState.DRAFT);
        ClassCurriculumBinding savedBinding = bindingRepository.save(binding);

        return toEditorResponse(classId, classOffering.getCourseId(), savedBinding, savedDraft, CurriculumResolutionService.SOURCE_CLASS_DRAFT);
    }

    /**
     * Creates and persists an empty CLASS-scoped draft version and snapshots the source
     * structure into thin sections + entries (no lesson rows cloned).
     */
    @Transactional
    private CurriculumVersion createDraftVersion(ClassOffering classOffering, UserAccount trainer, CurriculumVersion source) {
        CurriculumVersion draft = new CurriculumVersion();
        draft.setCourseId(classOffering.getCourseId());
        draft.setClassId(classOffering.getId());
        draft.setScope(CurriculumScope.CLASS);
        draft.setStatus(CurriculumStatus.DRAFT);
        draft.setVersionNumber(curriculumVersionRepository.findMaxClassVersionNumber(classOffering.getId(), CurriculumScope.CLASS) + 1);
        draft.setTitle(source.getTitle());
        draft.setSourceVersionId(source.getId());
        draft.setCreatedBy(trainer.getId());
        CurriculumVersion savedDraft = curriculumVersionRepository.save(draft);
        compositionService.snapshotStructure(savedDraft, source);
        return savedDraft;
    }

    /**
     * Returns the editable draft for the class, AUTO-INITIALIZING one from the current
     * effective source (class published, else published master) when the trainer has not
     * started a draft yet. Used by every write operation so the trainer can edit in place
     * without an explicit "start draft" step.
     */
    @Transactional
    private CurriculumVersion requireEditableDraftForWrite(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        ClassCurriculumBinding binding = requireBindingForUpdate(classId, classOffering.getCourseId());
        if (binding.getDraftVersionId() != null) {
            CurriculumVersion draft = curriculumVersionRepository.findById(binding.getDraftVersionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class curriculum draft not found"));
            assertDraftVersionForClass(draft, classOffering.getCourseId(), classId);
            return draft;
        }
        CurriculumVersion source = resolutionService.resolveDraftInitializationSource(
                classOffering.getCourseId(), classId, trainer.getId());
        CurriculumVersion savedDraft = createDraftVersion(classOffering, trainer, source);
        binding.setDraftVersionId(savedDraft.getId());
        binding.setCustomizationState(CurriculumCustomizationState.DRAFT);
        bindingRepository.save(binding);
        return savedDraft;
    }

    @Transactional
    public SectionResponse createSection(UUID classId, SectionRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(draft);
        section.setTitle(CurriculumRequestNormalizer.normalizeRequired(request.title(), "Section title is required"));
        section.setSortOrder(request.sortOrder() == null
                ? sectionRepository.findMaxSortOrderByCurriculumVersionId(draft.getId()) + 1
                : request.sortOrder());
        return mapper.toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse updateSection(UUID classId, UUID sectionId, SectionRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        section.setTitle(CurriculumRequestNormalizer.normalizeRequired(request.title(), "Section title is required"));
        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }
        return mapper.toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(UUID classId, UUID sectionId) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        sectionRepository.delete(section);
    }

    @Transactional
    public List<SectionResponse> reorderSections(UUID classId, ReorderRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        List<CurriculumSection> sections = sectionRepository.findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(draft.getId());
        Map<UUID, CurriculumSection> sectionsById = sections.stream()
                .collect(LinkedHashMap::new, (map, section) -> map.put(section.getId(), section), LinkedHashMap::putAll);
        CurriculumReorderValidator.assertMatchesAllItems(request.ids(), sectionsById.keySet(), "Section");

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
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumSection section = requireDraftSection(sectionId, draft.getId());

        // A class-only lesson has no master to inherit: create the entry and materialize a real row.
        ClassCurriculumEntry entry = new ClassCurriculumEntry();
        entry.setSection(section);
        entry.setLessonIdentityId(UUID.randomUUID());
        entry.setSortOrder(request.sortOrder() == null
                ? entryRepository.findMaxSortOrderByClassVersionIdAndSectionId(draft.getId(), section.getId()) + 1
                : request.sortOrder());
        ClassCurriculumEntry savedEntry = entryRepository.save(entry);

        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setSection(section);
        lesson.setLessonIdentityId(savedEntry.getLessonIdentityId());
        lesson.setSortOrder(savedEntry.getSortOrder());
        applyLessonRequest(lesson, request, true);
        CurriculumLesson savedLesson = lessonRepository.save(lesson);
        lessonTestLinkService.ensureQuizTest(savedLesson);

        savedEntry.setMaterializedLessonId(savedLesson.getId());
        entryRepository.save(savedEntry);

        return mapper.toLessonResponse(savedLesson);
    }

    @Transactional
    public LessonResponse updateLesson(UUID classId, UUID lessonId, LessonRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumLesson lesson = compositionService.ensureMaterializedForWrite(draft, lessonId);
        applyLessonRequest(lesson, request, false);
        if (request.sortOrder() != null) {
            lesson.setSortOrder(request.sortOrder());
        }
        CurriculumLesson saved = lessonRepository.save(lesson);
        lessonTestLinkService.ensureQuizTest(saved);
        syncEntrySortOrder(saved);
        return mapper.toLessonResponse(saved);
    }

    @Transactional
    public void deleteLesson(UUID classId, UUID lessonId) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        ClassCurriculumEntry entry = requireDraftEntryForLessonReference(draft.getId(), lessonId);
        entry.setDeletedAt(Instant.now());
        entryRepository.save(entry);
        if (entry.getMaterializedLessonId() != null) {
            lessonRepository.deleteById(entry.getMaterializedLessonId());
        }
    }

    @Transactional
    public List<LessonResponse> reorderLessons(UUID classId, UUID sectionId, ReorderRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        requireDraftSection(sectionId, draft.getId());
        List<ClassCurriculumEntry> entries =
                entryRepository.findByClassVersionIdAndSectionIdOrderBySortOrderAsc(draft.getId(), sectionId);
        Map<UUID, ClassCurriculumEntry> entriesByRefId = new LinkedHashMap<>();
        for (ClassCurriculumEntry entry : entries) {
            entriesByRefId.put(exposedLessonRefId(entry), entry);
        }
        CurriculumReorderValidator.assertMatchesAllItems(request.ids(), entriesByRefId.keySet(), "Lesson");

        int sortOrder = 0;
        for (UUID requestedId : request.ids()) {
            ClassCurriculumEntry entry = entriesByRefId.get(requestedId);
            entry.setSortOrder(sortOrder++);
            if (entry.getMaterializedLessonId() != null) {
                lessonRepository.findById(entry.getMaterializedLessonId()).ifPresent(lesson -> {
                    lesson.setSortOrder(entry.getSortOrder());
                    lessonRepository.save(lesson);
                });
            }
        }
        entryRepository.saveAll(entries);

        CurriculumSection section = requireDraftSection(sectionId, draft.getId());
        return compositionService.effectiveLessons(section).stream()
                .map(mapper::toLessonResponse)
                .toList();
    }

    @Transactional
    public LessonResourceResponse addResource(UUID classId, UUID lessonId, LessonResourceRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumLesson lesson = compositionService.ensureMaterializedForWrite(draft, lessonId);
        if (lesson.getResources().size() >= MAX_RESOURCES_PER_LESSON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
        }
        CurriculumLessonResource resource = LessonResourceBuilder.create(request, LessonResourceBuilder.nextSortOrder(lesson.getResources()));
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
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumLesson lesson = compositionService.ensureMaterializedForWrite(draft, lessonId);
        List<LessonResourceRequest> safeRequests = requests == null ? List.of() : requests;
        if (safeRequests.size() > MAX_RESOURCES_PER_LESSON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
        }
        lesson.getResources().clear();
        IntStream.range(0, safeRequests.size())
                .mapToObj(index -> LessonResourceBuilder.create(safeRequests.get(index), index))
                .forEach(lesson::addResource);
        CurriculumLesson saved = lessonRepository.save(lesson);
        return saved.getResources().stream()
                .sorted(Comparator.comparing(CurriculumLessonResource::getSortOrder))
                .map(mapper::toLessonResourceResponse)
                .toList();
    }

    @Transactional
    public void removeResource(UUID classId, UUID lessonId, UUID resourceId) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumLesson lesson = compositionService.ensureMaterializedForWrite(draft, lessonId);
        boolean removed = lesson.getResources().removeIf(resource ->
                resourceId.equals(resource.getId())
                        || resourceId.equals(resource.getSourceCurriculumResourceId()));
        if (!removed) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Resource was not found");
        }
        lessonRepository.save(lesson);
    }

    @Transactional
    public List<LessonResourceResponse> reorderResources(UUID classId, UUID lessonId, ReorderRequest request) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        CurriculumLesson lesson = compositionService.ensureMaterializedForWrite(draft, lessonId);
        Map<UUID, CurriculumLessonResource> resourcesById = new LinkedHashMap<>();
        for (CurriculumLessonResource resource : lesson.getResources()) {
            resourcesById.put(exposedResourceRefId(resource), resource);
        }
        CurriculumReorderValidator.assertMatchesAllItems(request.ids(), resourcesById.keySet(), "Resource");

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

    @Transactional
    public CurriculumLesson requireOwnedClassLessonForWrite(UUID classId, UUID lessonId) {
        CurriculumVersion draft = requireEditableDraftForWrite(classId);
        return compositionService.ensureMaterializedForWrite(draft, lessonId);
    }

    @Transactional(readOnly = true)
    public CurriculumLesson requireOwnedClassLessonForRead(UUID classId, UUID lessonId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();
        ClassOffering classOffering = requireOwnedClass(classId, trainer.getId());
        CurriculumResolution resolution = resolutionService.resolveTrainerEditing(
                classOffering.getCourseId(),
                classId,
                trainer.getId()
        );
        return compositionService.resolveEffectiveLesson(resolution.version(), lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
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

        Instant now = Instant.now();
        if (binding.getPublishedVersionId() != null
                && !binding.getPublishedVersionId().equals(draft.getId())) {
            curriculumVersionRepository.findByIdForUpdate(binding.getPublishedVersionId())
                    .ifPresent(previouslyPublished -> {
                        previouslyPublished.setStatus(CurriculumStatus.ARCHIVED);
                        previouslyPublished.setArchivedAt(now);
                        curriculumVersionRepository.save(previouslyPublished);
                    });
            curriculumVersionRepository.flush();
        }

        draft.setStatus(CurriculumStatus.PUBLISHED);
        draft.setPublishedAt(now);
        CurriculumVersion published = curriculumVersionRepository.save(draft);

        binding.setPublishedVersionId(published.getId());
        binding.setDraftVersionId(null);
        binding.setCustomizationState(CurriculumCustomizationState.PUBLISHED);
        ClassCurriculumBinding savedBinding = bindingRepository.save(binding);

        return toEditorResponse(classId, classOffering.getCourseId(), savedBinding, published, CurriculumResolutionService.SOURCE_CLASS_PUBLISHED);
    }

    // ========== Private Helper Methods ==========

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
                .orElseGet(() -> {
                    bindingProvisioningService.ensureBinding(classId, courseId);
                    return bindingRepository.findByClassIdForUpdate(classId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.RESOURCE_NOT_FOUND,
                                    "Class curriculum binding could not be initialized"));
                });
        if (!courseId.equals(binding.getCourseId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum binding is inconsistent");
        }
        return binding;
    }

    private CurriculumSection requireDraftSection(UUID sectionId, UUID draftVersionId) {
        return sectionRepository.findByIdAndCurriculumVersionId(sectionId, draftVersionId)
                .or(() -> sectionRepository.findBySourceCurriculumSectionIdAndCurriculumVersionId(sectionId, draftVersionId))
                .or(() -> sectionRepository.findBySourceModuleIdAndCurriculumVersionId(sectionId, draftVersionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
    }

    private ClassCurriculumEntry requireDraftEntryForLessonReference(UUID draftVersionId, UUID lessonReferenceId) {
        return entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(draftVersionId, lessonReferenceId)
                .or(() -> entryRepository.findByClassVersionIdAndLessonIdentityId(draftVersionId, lessonReferenceId))
                .or(() -> entryRepository.findByMaterializedLessonId(lessonReferenceId)
                        .filter(entry -> draftVersionId.equals(entry.getClassVersionId())))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
    }

    /** The lesson id a client sees for an entry: materialized row id, master id (inherited), or identity. */
    private UUID exposedLessonRefId(ClassCurriculumEntry entry) {
        if (entry.getMaterializedLessonId() != null) {
            return entry.getMaterializedLessonId();
        }
        if (entry.getSourceCurriculumLessonId() != null) {
            return entry.getSourceCurriculumLessonId();
        }
        return entry.getLessonIdentityId();
    }

    /** The resource id a client sees: the master resource id for copied resources, else the real row id. */
    private UUID exposedResourceRefId(CurriculumLessonResource resource) {
        return resource.getSourceCurriculumResourceId() != null
                ? resource.getSourceCurriculumResourceId()
                : resource.getId();
    }

    private void syncEntrySortOrder(CurriculumLesson lesson) {
        entryRepository.findByMaterializedLessonId(lesson.getId())
                .ifPresent(entry -> {
                    entry.setSortOrder(lesson.getSortOrder());
                    entryRepository.save(entry);
                });
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
        lesson.setTitle(CurriculumRequestNormalizer.normalizeRequired(request.title(), "Lesson title is required"));
        String currentVideoUrl = lesson.getVideoUrl();
        LessonType newType = CurriculumParseService.parseLessonType(request,
                create ? LessonType.RICH_TEXT : lesson.getType());
        lesson.setType(newType);
        lesson.setVideoUrl(videoSummaryService.normalizeLessonVideoUrl(
                currentVideoUrl,
                request.videoUrl(),
                newType == LessonType.VIDEO
        ));
        String content = CurriculumRequestNormalizer.normalizeNullable(request.content());
        if (newType == LessonType.QUIZ) {
            quizContentValidator.validate(content);
        }
        lesson.setContent(content);
        lesson.setAttachmentUrl(CurriculumRequestNormalizer.normalizeNullable(request.attachmentUrl()));
        lesson.setDurationSeconds(request.durationSeconds());
        if (create || request.isPreview() != null) {
            lesson.setPreview(Boolean.TRUE.equals(request.isPreview()));
        }
        lesson.setStatus(CurriculumParseService.parseLessonStatus(request.status(),
                create ? LessonStatus.DRAFT : lesson.getStatus()));
        if (request.resources() != null) {
            if (request.resources().size() > MAX_RESOURCES_PER_LESSON) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson resources must not exceed 10 files");
            }
            lesson.getResources().clear();
            IntStream.range(0, request.resources().size())
                    .mapToObj(index -> LessonResourceBuilder.create(request.resources().get(index), index))
                    .forEach(lesson::addResource);
        }
    }
}
