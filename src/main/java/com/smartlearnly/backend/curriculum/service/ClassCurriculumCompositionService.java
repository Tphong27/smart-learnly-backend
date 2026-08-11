package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumEntryRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the effective lesson tree of a CLASS-scoped curriculum version in the
 * reference + delta composition model.
 *
 * <p>A class version stores thin {@link ClassCurriculumEntry} references instead of a deep
 * clone of the master tree. An entry either inherits a master lesson (content resolves against
 * the current published master at read time, so it follows master updates) or owns a
 * materialized {@link CurriculumLesson} row (created on first edit or artifact pin —
 * {@code materialize-on-write}). This service composes both into a single effective lesson
 * list and materializes inherited lessons when the writer needs a real row.
 */
@Service
@RequiredArgsConstructor
public class ClassCurriculumCompositionService {

    private final ClassCurriculumEntryRepository entryRepository;
    private final CurriculumLessonRepository lessonRepository;
    private final CurriculumSectionRepository sectionRepository;
    private final CurriculumVersionRepository versionRepository;

    /**
     * Whether a version is governed by the composition model. Every CLASS-scoped version is a
     * composition after the V86 backfill (or is created empty by snapshotStructure), so the
     * scope check alone is sufficient and avoids a per-read existence query.
     */
    public boolean isCompositionVersion(CurriculumVersion version) {
        return version.getScope() == CurriculumScope.CLASS;
    }

    /**
     * Replaces the old deep clone: snapshots the ordered structure of {@code source} into the
     * given empty class {@code draft} as thin section rows + entries. No curriculum_lessons
     * rows are created; content stays inherited by reference.
     *
     * <p>The source may be a MASTER version (snapshot its real lessons) or a CLASS composition
     * version (mirror its effective entries; materialized lessons are copied into real rows so
     * class-owned content carries over into the new draft).
     */
    @Transactional
    public void snapshotStructure(CurriculumVersion draft, CurriculumVersion source) {
        if (source.getScope() == CurriculumScope.MASTER) {
            snapshotFromMaster(draft, source);
        } else {
            snapshotFromClassVersion(draft, source);
        }
    }

    private void snapshotFromMaster(CurriculumVersion draft, CurriculumVersion source) {
        orderedSections(source).forEach(sourceSection -> {
            CurriculumSection savedSection = sectionRepository.save(copySectionRow(draft, sourceSection));
            orderedLessons(sourceSection).forEach(sourceLesson -> {
                ClassCurriculumEntry entry = new ClassCurriculumEntry();
                entry.setSection(savedSection);
                entry.setSourceCurriculumLessonId(sourceLesson.getId());
                entry.setLessonIdentityId(sourceLesson.getLessonIdentityId());
                entry.setSortOrder(sourceLesson.getSortOrder());
                entryRepository.save(entry);
            });
        });
    }

    private void snapshotFromClassVersion(CurriculumVersion draft, CurriculumVersion source) {
        orderedSections(source).forEach(sourceSection -> {
            CurriculumSection savedSection = sectionRepository.save(copySectionRow(draft, sourceSection));
            entryRepository.findByClassVersionIdAndSectionIdOrderBySortOrderAsc(source.getId(), sourceSection.getId())
                    .forEach(sourceEntry -> {
                        ClassCurriculumEntry entry = new ClassCurriculumEntry();
                        entry.setSection(savedSection);
                        entry.setSourceCurriculumLessonId(sourceEntry.getSourceCurriculumLessonId());
                        entry.setLessonIdentityId(sourceEntry.getLessonIdentityId());
                        entry.setSortOrder(sourceEntry.getSortOrder());
                        entry.setHidden(sourceEntry.getHidden());
                        if (sourceEntry.getMaterializedLessonId() != null) {
                            lessonRepository.findById(sourceEntry.getMaterializedLessonId()).ifPresent(sourceRow -> {
                                CurriculumLesson row = copyMaterializedLesson(sourceRow, savedSection, entry);
                                CurriculumLesson savedRow = lessonRepository.save(row);
                                entry.setMaterializedLessonId(savedRow.getId());
                            });
                        }
                        entryRepository.save(entry);
                    });
        });
    }

    /**
     * Effective lessons of one section of a composition version, in entry order. Inherited
     * entries are resolved against the current published master; entries whose master lesson is
     * gone (dangling) are omitted. Materialized rows are returned as-is.
     */
    @Transactional(readOnly = true)
    public List<CurriculumLesson> effectiveLessons(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        if (!isCompositionVersion(version)) {
            return orderedLessons(section);
        }
        List<ClassCurriculumEntry> entries =
                entryRepository.findByClassVersionIdAndSectionIdOrderBySortOrderAsc(version.getId(), section.getId());
        if (entries.isEmpty()) {
            return List.of();
        }

        List<ClassCurriculumEntry> inheritedEntries = entries.stream()
                .filter(entry -> entry.getMaterializedLessonId() == null)
                .toList();
        return composeEffectiveLessons(
                entries,
                loadMaterializedLessons(entries),
                resolveMasterLessonsByIdentity(version, inheritedEntries));
    }

    /**
     * All effective lessons of a composition version, flattened in section order. Used by
     * progress and reporting to count the real visible lesson set.
     */
    @Transactional(readOnly = true)
    public List<CurriculumLesson> orderedEffectiveLessons(CurriculumVersion version) {
        if (!isCompositionVersion(version)) {
            return version.getSections().stream().flatMap(section -> orderedLessons(section).stream()).toList();
        }
        List<ClassCurriculumEntry> entries =
                entryRepository.findByClassVersionIdOrderBySortOrderAscCreatedAtAsc(version.getId());
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ClassCurriculumEntry>> entriesBySection = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.getSection().getId()));
        Map<UUID, CurriculumLesson> materializedById = loadMaterializedLessons(entries);
        Map<UUID, CurriculumLesson> masterByIdentity = resolveMasterLessonsByIdentity(
                version,
                entries.stream().filter(entry -> entry.getMaterializedLessonId() == null).toList());

        return orderedSections(version).stream()
                .flatMap(section -> composeEffectiveLessons(
                        entriesBySection.getOrDefault(section.getId(), List.of()),
                        materializedById,
                        masterByIdentity).stream())
                .toList();
    }

    /** Tải toàn bộ lesson đã materialize trong một lần truy vấn. */
    private Map<UUID, CurriculumLesson> loadMaterializedLessons(List<ClassCurriculumEntry> entries) {
        List<UUID> materializedIds = entries.stream()
                .map(ClassCurriculumEntry::getMaterializedLessonId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (materializedIds.isEmpty()) {
            return Map.of();
        }
        return lessonRepository.findAllById(materializedIds).stream()
                .collect(Collectors.toMap(CurriculumLesson::getId, Function.identity()));
    }

    /** Ghép entry với lesson materialize hoặc nội dung master đã được batch-load. */
    private List<CurriculumLesson> composeEffectiveLessons(
            List<ClassCurriculumEntry> entries,
            Map<UUID, CurriculumLesson> materializedById,
            Map<UUID, CurriculumLesson> masterByIdentity) {
        List<CurriculumLesson> result = new ArrayList<>(entries.size());
        for (ClassCurriculumEntry entry : entries) {
            CurriculumLesson lesson = entry.getMaterializedLessonId() == null
                    ? masterByIdentity.get(entry.getLessonIdentityId())
                    : materializedById.get(entry.getMaterializedLessonId());
            if (lesson == null) {
                continue;
            }
            result.add(entry.getMaterializedLessonId() == null
                    ? buildTransientInheritedLesson(entry, lesson)
                    : lesson);
        }
        return result;
    }

    /**
     * Resolves a lesson reference (materialized row id, master id, sourceLessonId or identity)
     * to the effective lesson of the given version. Replaces the semantics of
     * CurriculumLessonRepository.findEffectiveLessonReference for composition versions.
     */
    @Transactional(readOnly = true)
    public Optional<CurriculumLesson> resolveEffectiveLesson(CurriculumVersion version, UUID lessonReferenceId) {
        if (!isCompositionVersion(version)) {
            return lessonRepository.findEffectiveLessonReference(version.getId(), lessonReferenceId);
        }
        Optional<ClassCurriculumEntry> entry = findEntryForLessonReference(version.getId(), lessonReferenceId);
        if (entry.isPresent()) {
            return effectiveLessonForEntry(entry.get());
        }
        // Deliberately no fallback to findEffectiveLessonReference here: for a composition
        // version the legacy shadow rows (kept until the cleanup migration) must not let a
        // deleted/inherited lesson be resurrected via its row id or identity.
        return Optional.empty();
    }

    /**
     * Materializes an inherited entry into a real curriculum_lessons row, copying the current
     * master content, then returns it. Subsequent edits/pins target the materialized row and
     * the lesson is frozen from master. Safe for the write path (entry is row-locked).
     */
    @Transactional
    public CurriculumLesson ensureMaterializedForWrite(CurriculumVersion draft, UUID lessonReferenceId) {
        ClassCurriculumEntry entry = findEntryForLessonReference(draft.getId(), lessonReferenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        return materializeLesson(draft, entry);
    }

    private Optional<ClassCurriculumEntry> findEntryForLessonReference(UUID classVersionId, UUID lessonReferenceId) {
        Optional<ClassCurriculumEntry> bySource =
                entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(classVersionId, lessonReferenceId);
        if (bySource.isPresent()) {
            return bySource;
        }
        Optional<ClassCurriculumEntry> byIdentity =
                entryRepository.findByClassVersionIdAndLessonIdentityId(classVersionId, lessonReferenceId);
        if (byIdentity.isPresent()) {
            return byIdentity;
        }
        Optional<ClassCurriculumEntry> byLegacySourceLesson = lessonRepository.findFirstBySourceLessonId(lessonReferenceId)
                .flatMap(lesson -> entryRepository
                        .findByClassVersionIdAndSourceCurriculumLessonId(classVersionId, lesson.getId())
                        .or(() -> entryRepository.findByClassVersionIdAndLessonIdentityId(
                                classVersionId,
                                lesson.getLessonIdentityId())));
        if (byLegacySourceLesson.isPresent()) {
            return byLegacySourceLesson;
        }
        Optional<ClassCurriculumEntry> byLegacyLessonRow = lessonRepository.findById(lessonReferenceId)
                .flatMap(lesson -> entryRepository
                        .findByClassVersionIdAndLessonIdentityId(classVersionId, lesson.getLessonIdentityId())
                        .or(() -> lesson.getSourceCurriculumLessonId() == null
                                ? Optional.empty()
                                : entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(
                                        classVersionId,
                                        lesson.getSourceCurriculumLessonId()))
                        .or(() -> lesson.getSourceLessonId() == null
                                ? Optional.empty()
                                : lessonRepository.findFirstBySourceLessonId(lesson.getSourceLessonId())
                                        .flatMap(sourceLesson -> entryRepository
                                                .findByClassVersionIdAndSourceCurriculumLessonId(
                                                        classVersionId,
                                                        sourceLesson.getId()))));
        if (byLegacyLessonRow.isPresent()) {
            return byLegacyLessonRow;
        }
        return entryRepository.findByMaterializedLessonId(lessonReferenceId)
                .filter(entry -> classVersionId.equals(entry.getClassVersionId()));
    }

    private Optional<CurriculumLesson> effectiveLessonForEntry(ClassCurriculumEntry entry) {
        if (entry.getMaterializedLessonId() != null) {
            return lessonRepository.findById(entry.getMaterializedLessonId());
        }
        return resolveMasterLesson(entry).map(master -> buildTransientInheritedLesson(entry, master));
    }

    @Transactional
    private CurriculumLesson materializeLesson(CurriculumVersion draft, ClassCurriculumEntry entry) {
        ClassCurriculumEntry locked = entryRepository.findByIdAndClassVersionIdForUpdate(entry.getId(), draft.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (locked.getMaterializedLessonId() != null) {
            return lessonRepository.findById(locked.getMaterializedLessonId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        }

        CurriculumLesson master = resolveMasterLesson(locked)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Source lesson no longer exists on the published curriculum"));

        // Reuse a legacy shadow row (old clone) if present for the same identity, else create a new row.
        CurriculumLesson row = lessonRepository
                .findByCurriculumVersionIdAndLessonIdentityId(draft.getId(), locked.getLessonIdentityId())
                .orElseGet(() -> {
                    CurriculumLesson created = new CurriculumLesson();
                    created.setSection(locked.getSection());
                    created.setLessonIdentityId(locked.getLessonIdentityId());
                    created.setSourceCurriculumLessonId(locked.getSourceCurriculumLessonId());
                    created.setSortOrder(locked.getSortOrder());
                    return created;
                });
        boolean isNew = row.getId() == null;
        if (isNew) {
            row.setSection(locked.getSection());
            row.setLessonIdentityId(locked.getLessonIdentityId());
            row.setSourceCurriculumLessonId(locked.getSourceCurriculumLessonId());
            row.setSortOrder(locked.getSortOrder());
        }
        copyMasterContentInto(row, master, isNew);
        CurriculumLesson saved = lessonRepository.save(row);

        locked.setMaterializedLessonId(saved.getId());
        entryRepository.save(locked);
        return saved;
    }

    private void copyMasterContentInto(CurriculumLesson row, CurriculumLesson master, boolean replaceResources) {
        row.setSourceLessonId(master.getSourceLessonId());
        row.setTitle(master.getTitle());
        row.setType(master.getType());
        row.setVideoUrl(master.getVideoUrl());
        row.setContent(master.getContent());
        row.setAttachmentUrl(master.getAttachmentUrl());
        row.setDurationSeconds(master.getDurationSeconds());
        row.setPreview(master.getPreview());
        row.setStatus(master.getStatus());
        row.setTestId(master.getTestId());
        if (replaceResources) {
            row.getResources().clear();
            master.getResources().forEach(resource -> row.addResource(copyResource(resource)));
        }
    }

    private Optional<CurriculumLesson> resolveMasterLesson(ClassCurriculumEntry entry) {
        Optional<CurriculumVersion> classVersion = versionRepository.findById(entry.getClassVersionId());
        if (classVersion.isEmpty()) {
            return Optional.empty();
        }
        Optional<CurriculumVersion> masterVersion = versionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        classVersion.get().getCourseId(), CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        if (masterVersion.isPresent()) {
            Optional<CurriculumLesson> byIdentity = lessonRepository
                    .findByCurriculumVersionIdAndLessonIdentityId(
                            masterVersion.get().getId(), entry.getLessonIdentityId());
            if (byIdentity.isPresent()) {
                return byIdentity;
            }
        }
        // Inherited lessons resolve ONLY against the current published master by identity.
        // No source-id fallback: a stale/archived master row must not be resurrected, and the
        // dangling policy (entry omitted from the effective view) stays consistent everywhere.
        return Optional.empty();
    }

    private Map<UUID, CurriculumLesson> resolveMasterLessonsByIdentity(
            CurriculumVersion classVersion, List<ClassCurriculumEntry> entries) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        Optional<CurriculumVersion> masterVersion = versionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        classVersion.getCourseId(), CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        if (masterVersion.isEmpty()) {
            return Map.of();
        }
        List<UUID> identities = entries.stream()
                .map(ClassCurriculumEntry::getLessonIdentityId)
                .toList();
        return lessonRepository
                .findByCurriculumVersionIdAndLessonIdentityIdIn(masterVersion.get().getId(), identities)
                .stream()
                .collect(Collectors.toMap(CurriculumLesson::getLessonIdentityId, Function.identity()));
    }

    private CurriculumLesson buildTransientInheritedLesson(ClassCurriculumEntry entry, CurriculumLesson master) {
        CurriculumLesson transientLesson = new CurriculumLesson();
        transientLesson.setId(master.getId());
        transientLesson.setSection(entry.getSection());
        transientLesson.setCurriculumVersionId(master.getCurriculumVersionId());
        transientLesson.setLessonIdentityId(entry.getLessonIdentityId());
        transientLesson.setSourceLessonId(master.getSourceLessonId());
        transientLesson.setSourceCurriculumLessonId(entry.getSourceCurriculumLessonId());
        transientLesson.setTitle(master.getTitle());
        transientLesson.setType(master.getType());
        transientLesson.setVideoUrl(master.getVideoUrl());
        transientLesson.setContent(master.getContent());
        transientLesson.setAttachmentUrl(master.getAttachmentUrl());
        transientLesson.setDurationSeconds(master.getDurationSeconds());
        transientLesson.setPreview(master.getPreview());
        transientLesson.setStatus(master.getStatus());
        transientLesson.setTestId(master.getTestId());
        transientLesson.setSortOrder(entry.getSortOrder());
        master.getResources().forEach(resource -> {
            CurriculumLessonResource copy = copyResource(resource);
            // Surface the master resource id so the trainer view is stable and resource
            // operations match the materialized copy via sourceCurriculumResourceId.
            copy.setId(resource.getId());
            transientLesson.addResource(copy);
        });
        transientLesson.setCreatedAt(master.getCreatedAt());
        transientLesson.setUpdatedAt(master.getUpdatedAt());
        return transientLesson;
    }

    private CurriculumLesson copyMaterializedLesson(CurriculumLesson source, CurriculumSection targetSection, ClassCurriculumEntry entry) {
        CurriculumLesson row = new CurriculumLesson();
        row.setSection(targetSection);
        row.setLessonIdentityId(entry.getLessonIdentityId());
        row.setSourceCurriculumLessonId(entry.getSourceCurriculumLessonId());
        row.setSourceLessonId(source.getSourceLessonId());
        row.setSortOrder(entry.getSortOrder());
        copyMasterContentInto(row, source, true);
        return row;
    }

    private CurriculumLessonResource copyResource(CurriculumLessonResource source) {
        CurriculumLessonResource resource = new CurriculumLessonResource();
        resource.setSourceResourceId(source.getSourceResourceId());
        resource.setSourceCurriculumResourceId(source.getId());
        resource.setUrl(source.getUrl());
        resource.setObjectPath(source.getObjectPath());
        resource.setName(source.getName());
        resource.setFileSize(source.getFileSize());
        resource.setContentType(source.getContentType());
        resource.setSortOrder(source.getSortOrder());
        return resource;
    }

    private CurriculumSection copySectionRow(CurriculumVersion draft, CurriculumSection source) {
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(draft);
        section.setTitle(source.getTitle());
        section.setSortOrder(source.getSortOrder());
        section.setSourceCurriculumSectionId(source.getId());
        section.setSourceModuleId(source.getSourceModuleId());
        return section;
    }

    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(sectionComparator())
                .toList();
    }

    private List<CurriculumLesson> orderedLessons(CurriculumSection section) {
        return section.getLessons().stream()
                .sorted(lessonComparator())
                .toList();
    }

    private Comparator<CurriculumSection> sectionComparator() {
        return Comparator
                .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo));
    }

    private Comparator<CurriculumLesson> lessonComparator() {
        return Comparator
                .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo));
    }
}
