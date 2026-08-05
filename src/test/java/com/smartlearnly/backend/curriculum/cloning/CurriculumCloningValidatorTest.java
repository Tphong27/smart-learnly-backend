package com.smartlearnly.backend.curriculum.cloning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurriculumCloningValidatorTest {

    private CurriculumCloningValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CurriculumCloningValidator();
    }

    @Test
    void validateSourceVersion_validVersion_succeeds() {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());

        validator.validateSourceVersion(version);
    }

    @Test
    void validateSourceVersion_nullVersion_throws() {
        assertThatThrownBy(() -> validator.validateSourceVersion(null))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Source curriculum version is required");
    }

    @Test
    void validateSourceVersion_nullId_throws() {
        CurriculumVersion version = new CurriculumVersion();

        assertThatThrownBy(() -> validator.validateSourceVersion(version))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("must have an ID");
    }

    @Test
    void validateClassId_validId_succeeds() {
        UUID classId = UUID.randomUUID();

        validator.validateClassId(classId);
    }

    @Test
    void validateClassId_nullId_throws() {
        assertThatThrownBy(() -> validator.validateClassId(null))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Class id is required");
    }

    @Test
    void validateCreatorId_validId_succeeds() {
        UUID createdBy = UUID.randomUUID();

        validator.validateCreatorId(createdBy);
    }

    @Test
    void validateCreatorId_nullId_throws() {
        assertThatThrownBy(() -> validator.validateCreatorId(null))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Creator id is required");
    }

    @Test
    void validateCloningInputs_allValid_succeeds() {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        UUID classId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        validator.validateCloningInputs(version, classId, createdBy);
    }

    @Test
    void validateCloningInputs_nullSource_throws() {
        UUID classId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validateCloningInputs(null, classId, createdBy))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Source curriculum version is required");
    }

    @Test
    void validateCloningInputs_nullClassId_throws() {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        UUID createdBy = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validateCloningInputs(version, null, createdBy))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Class id is required");
    }

    @Test
    void validateCloningInputs_nullCreatorId_throws() {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        UUID classId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validateCloningInputs(version, classId, null))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessageContaining("Creator id is required");
    }
}
