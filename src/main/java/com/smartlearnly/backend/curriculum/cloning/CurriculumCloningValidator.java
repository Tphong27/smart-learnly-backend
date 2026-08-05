package com.smartlearnly.backend.curriculum.cloning;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates inputs for curriculum cloning operations.
 */
@Component
public class CurriculumCloningValidator {

    /**
     * Validates that source version is valid for cloning.
     */
    public void validateSourceVersion(CurriculumVersion sourceVersion) {
        if (sourceVersion == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Source curriculum version is required");
        }
        if (sourceVersion.getId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Source curriculum version must have an ID");
        }
    }

    /**
     * Validates that target class ID is valid.
     */
    public void validateClassId(UUID classId) {
        if (classId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Class id is required");
        }
    }

    /**
     * Validates that creator ID is valid.
     */
    public void validateCreatorId(UUID createdBy) {
        if (createdBy == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Creator id is required");
        }
    }

    /**
     * Validates all inputs for cloning operation.
     */
    public void validateCloningInputs(CurriculumVersion sourceVersion, UUID classId, UUID createdBy) {
        validateSourceVersion(sourceVersion);
        validateClassId(classId);
        validateCreatorId(createdBy);
    }
}
