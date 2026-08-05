package com.smartlearnly.backend.curriculum.cloning;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Copies curriculum entities for cloning operations.
 * Pure transformation logic with no business rules or persistence.
 */
@Component
public class CurriculumEntityCopier {

    /**
     * Creates a new curriculum version as a class draft from a source version.
     *
     * @param source the source version to copy
     * @param classId the target class ID
     * @param createdBy the creator ID
     * @param nextVersionNumber the next version number for this class
     * @return the copied version (not persisted)
     */
    public CurriculumVersion copyVersionToClassDraft(
            CurriculumVersion source,
            UUID classId,
            UUID createdBy,
            int nextVersionNumber) {

        CurriculumVersion target = new CurriculumVersion();
        target.setCourseId(source.getCourseId());
        target.setClassId(classId);
        target.setScope(com.smartlearnly.backend.curriculum.entity.CurriculumScope.CLASS);
        target.setStatus(com.smartlearnly.backend.curriculum.entity.CurriculumStatus.DRAFT);
        target.setVersionNumber(nextVersionNumber);
        target.setTitle(source.getTitle());
        target.setSourceVersionId(source.getId());
        target.setCreatedBy(createdBy);

        copySections(source, target);

        return target;
    }

    /**
     * Copies sections from source version to target version.
     */
    void copySections(CurriculumVersion source, CurriculumVersion target) {
        if (source.getSections() == null) {
            return;
        }

        source.getSections().stream()
                .sorted(sectionComparator())
                .map(this::copySection)
                .forEach(target::addSection);
    }

    /**
     * Copies a section with its lessons.
     */
    CurriculumSection copySection(CurriculumSection source) {
        CurriculumSection target = new CurriculumSection();
        target.setSourceModuleId(source.getSourceModuleId());
        target.setSourceCurriculumSectionId(source.getId());
        target.setTitle(source.getTitle());
        target.setSortOrder(source.getSortOrder());

        if (source.getLessons() != null) {
            source.getLessons().stream()
                    .sorted(lessonComparator())
                    .map(this::copyLesson)
                    .forEach(target::addLesson);
        }

        return target;
    }

    /**
     * Copies a lesson with its resources.
     */
    CurriculumLesson copyLesson(CurriculumLesson source) {
        CurriculumLesson target = new CurriculumLesson();
        target.setLessonIdentityId(source.getLessonIdentityId());
        target.setSourceLessonId(source.getSourceLessonId());
        target.setSourceCurriculumLessonId(source.getId());
        target.setTitle(source.getTitle());
        target.setType(source.getType());
        target.setVideoUrl(source.getVideoUrl());
        target.setContent(source.getContent());
        target.setAttachmentUrl(source.getAttachmentUrl());
        target.setDurationSeconds(source.getDurationSeconds());
        target.setPreview(source.getPreview());
        target.setStatus(source.getStatus());
        target.setSortOrder(source.getSortOrder());
        target.setTestId(source.getTestId());

        if (source.getResources() != null) {
            source.getResources().stream()
                    .sorted(resourceComparator())
                    .map(this::copyResource)
                    .forEach(target::addResource);
        }

        return target;
    }

    /**
     * Copies a resource.
     */
    CurriculumLessonResource copyResource(CurriculumLessonResource source) {
        CurriculumLessonResource target = new CurriculumLessonResource();
        target.setSourceResourceId(source.getSourceResourceId());
        target.setSourceCurriculumResourceId(source.getId());
        target.setUrl(source.getUrl());
        target.setObjectPath(source.getObjectPath());
        target.setName(source.getName());
        target.setFileSize(source.getFileSize());
        target.setContentType(source.getContentType());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private Comparator<CurriculumSection> sectionComparator() {
        return Comparator
                .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<CurriculumLesson> lessonComparator() {
        return Comparator
                .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<CurriculumLessonResource> resourceComparator() {
        return Comparator
                .comparing(CurriculumLessonResource::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CurriculumLessonResource::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
