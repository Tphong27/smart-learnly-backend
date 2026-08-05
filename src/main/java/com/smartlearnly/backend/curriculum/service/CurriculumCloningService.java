package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.curriculum.cloning.CurriculumCloningValidator;
import com.smartlearnly.backend.curriculum.cloning.CurriculumEntityCopier;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for cloning curriculum versions.
 * Delegates validation and copying to specialized components.
 */
@Service
@RequiredArgsConstructor
public class CurriculumCloningService {

    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CurriculumCloningValidator validator;
    private final CurriculumEntityCopier copier;

    /**
     * Clones a curriculum version to a class draft.
     *
     * @param sourceVersion the source version to clone
     * @param classId the target class ID
     * @param createdBy the creator ID
     * @return the cloned version (persisted)
     */
    @Transactional
    public CurriculumVersion cloneToClassDraft(CurriculumVersion sourceVersion, UUID classId, UUID createdBy) {
        validator.validateCloningInputs(sourceVersion, classId, createdBy);

        int nextVersionNumber = curriculumVersionRepository
                .findMaxClassVersionNumber(classId, CurriculumScope.CLASS) + 1;

        CurriculumVersion draft = copier.copyVersionToClassDraft(
                sourceVersion, classId, createdBy, nextVersionNumber);

        return curriculumVersionRepository.save(draft);
    }
}
