package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumCloningService {
    private final CurriculumVersionRepository curriculumVersionRepository;

    @Transactional
    public CurriculumVersion cloneToClassDraft(CurriculumVersion sourceVersion, UUID classId, UUID createdBy) {
        if (sourceVersion == null || sourceVersion.getId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source curriculum version is required");
        }
        if (classId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Class id is required");
        }

        CurriculumVersion draft = new CurriculumVersion();
        draft.setCourseId(sourceVersion.getCourseId());
        draft.setClassId(classId);
        draft.setScope(CurriculumScope.CLASS);
        draft.setStatus(CurriculumStatus.DRAFT);
        draft.setVersionNumber(nextClassVersionNumber(classId));
        draft.setTitle(sourceVersion.getTitle());
        draft.setSourceVersionId(sourceVersion.getId());
        draft.setCreatedBy(createdBy);

        sourceVersion.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copySection)
                .forEach(draft::addSection);

        return curriculumVersionRepository.save(draft);
    }

    private int nextClassVersionNumber(UUID classId) {
        return curriculumVersionRepository.findMaxClassVersionNumber(classId, CurriculumScope.CLASS) + 1;
    }

    private CurriculumSection copySection(CurriculumSection source) {
        CurriculumSection target = new CurriculumSection();
        target.setSourceSectionId(source.getSourceSectionId());
        target.setSourceCurriculumSectionId(source.getId());
        target.setTitle(source.getTitle());
        target.setSortOrder(source.getSortOrder());

        source.getLessons().stream()
                .sorted(Comparator
                        .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copyLesson)
                .forEach(target::addLesson);

        return target;
    }

    private CurriculumLesson copyLesson(CurriculumLesson source) {
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

        source.getResources().stream()
                .sorted(Comparator
                        .comparing(CurriculumLessonResource::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLessonResource::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copyResource)
                .forEach(target::addResource);

        return target;
    }

    private CurriculumLessonResource copyResource(CurriculumLessonResource source) {
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
}
