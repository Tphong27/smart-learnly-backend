package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers OpenSpec task 8.1: curriculum resolution never leaks DRAFT versions to trainees, falls back
 * to the published master when a class has no published class version, enforces trainer ownership,
 * and prefers the class published version when one exists.
 */
@ExtendWith(MockitoExtension.class)
class CurriculumResolutionServiceTest {
    @Mock
    private CurriculumVersionRepository curriculumVersionRepository;
    @Mock
    private ClassCurriculumBindingRepository bindingRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @InjectMocks
    private CurriculumResolutionService curriculumResolutionService;

    @Test
    void traineeLearningFallsBackToPublishedMasterWhenClassHasNoPublishedVersion() {
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID baseMasterVersionId = UUID.randomUUID();

        ClassOffering classOffering = classOffering(courseId, UUID.randomUUID());
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId)).thenReturn(Optional.of(classOffering));
        when(classEnrollmentRepository.findByClassIdAndStudentId(classId, studentId))
                .thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE)));

        // Binding has no published class version; base master points to a DRAFT so the resolver must
        // fall back to the latest PUBLISHED master version.
        ClassCurriculumBinding binding = binding(classId, courseId, baseMasterVersionId, null, null);
        when(bindingRepository.findByClassIdAndCourseId(classId, courseId)).thenReturn(Optional.of(binding));

        CurriculumVersion draftMaster = version(courseId, null, CurriculumScope.MASTER, CurriculumStatus.DRAFT);
        when(curriculumVersionRepository.findById(baseMasterVersionId)).thenReturn(Optional.of(draftMaster));

        CurriculumVersion publishedMaster = version(courseId, null, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.of(publishedMaster));

        CurriculumResolution resolution = curriculumResolutionService
                .resolveTraineeLearning(courseId, classId, studentId);

        assertThat(resolution.version()).isSameAs(publishedMaster);
        assertThat(resolution.version().getStatus()).isEqualTo(CurriculumStatus.PUBLISHED);
        assertThat(resolution.customized()).isFalse();
        assertThat(resolution.source()).isEqualTo(CurriculumResolutionService.SOURCE_MASTER_INHERITED);
    }

    @Test
    void traineeLearningReturnsClassPublishedVersionWhenPresent() {
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID publishedVersionId = UUID.randomUUID();

        ClassOffering classOffering = classOffering(courseId, UUID.randomUUID());
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId)).thenReturn(Optional.of(classOffering));
        when(classEnrollmentRepository.findByClassIdAndStudentId(classId, studentId))
                .thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE)));

        ClassCurriculumBinding binding = binding(classId, courseId, UUID.randomUUID(), null, publishedVersionId);
        when(bindingRepository.findByClassIdAndCourseId(classId, courseId)).thenReturn(Optional.of(binding));

        CurriculumVersion classPublished = version(courseId, classId, CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        when(curriculumVersionRepository.findById(publishedVersionId)).thenReturn(Optional.of(classPublished));

        CurriculumResolution resolution = curriculumResolutionService
                .resolveTraineeLearning(courseId, classId, studentId);

        assertThat(resolution.version()).isSameAs(classPublished);
        assertThat(resolution.version().getStatus()).isEqualTo(CurriculumStatus.PUBLISHED);
        assertThat(resolution.customized()).isTrue();
        assertThat(resolution.source()).isEqualTo(CurriculumResolutionService.SOURCE_CLASS_PUBLISHED);
    }

    @Test
    void trainerEditingRejectsTrainerWhoDoesNotOwnTheClass() {
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID ownerTrainerId = UUID.randomUUID();
        UUID otherTrainerId = UUID.randomUUID();

        ClassOffering classOffering = classOffering(courseId, ownerTrainerId);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId)).thenReturn(Optional.of(classOffering));

        assertThatThrownBy(() -> curriculumResolutionService
                .resolveTrainerEditing(courseId, classId, otherTrainerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ClassOffering classOffering(UUID courseId, UUID trainerId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(courseId);
        classOffering.setTrainerId(trainerId);
        return classOffering;
    }

    private ClassEnrollment enrollment(EnrollmentStatus status) {
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setStatus(status);
        return enrollment;
    }

    private ClassCurriculumBinding binding(
            UUID classId,
            UUID courseId,
            UUID baseMasterVersionId,
            UUID draftVersionId,
            UUID publishedVersionId) {
        ClassCurriculumBinding binding = new ClassCurriculumBinding();
        binding.setId(UUID.randomUUID());
        binding.setClassId(classId);
        binding.setCourseId(courseId);
        binding.setBaseMasterVersionId(baseMasterVersionId);
        binding.setDraftVersionId(draftVersionId);
        binding.setPublishedVersionId(publishedVersionId);
        return binding;
    }

    private CurriculumVersion version(UUID courseId, UUID classId, CurriculumScope scope, CurriculumStatus status) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setClassId(classId);
        version.setScope(scope);
        version.setStatus(status);
        version.setVersionNumber(1);
        return version;
    }
}
