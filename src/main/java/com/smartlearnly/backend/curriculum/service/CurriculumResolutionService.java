package com.smartlearnly.backend.curriculum.service;

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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumResolutionService {
    public static final String SOURCE_MASTER_PUBLIC = "master_public";
    public static final String SOURCE_MASTER_AUTHORING = "master_authoring";
    public static final String SOURCE_MASTER_INHERITED = "master_inherited";
    public static final String SOURCE_CLASS_DRAFT = "class_draft";
    public static final String SOURCE_CLASS_PUBLISHED = "class_published";

    private final CurriculumVersionRepository curriculumVersionRepository;
    private final ClassCurriculumBindingRepository bindingRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Transactional(readOnly = true)
    public CurriculumResolution resolvePublicMaster(UUID courseId) {
        CurriculumVersion version = findPublishedMaster(courseId);
        return new CurriculumResolution(version, null, null, false, SOURCE_MASTER_PUBLIC);
    }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveMasterAuthoring(UUID courseId) {
        CurriculumVersion version = curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(courseId, CurriculumScope.MASTER)
                .orElseGet(() -> findPublishedMaster(courseId));
        return new CurriculumResolution(version, null, null, false, SOURCE_MASTER_AUTHORING);
    }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveTrainerEditing(UUID courseId, UUID classId, UUID trainerId) {
        ClassOffering classOffering = requireClassForCourse(courseId, classId);
        requireTrainerOwner(classOffering, trainerId);

        ClassCurriculumBinding binding = requireBinding(classId, courseId);
        if (binding.getDraftVersionId() != null) {
            CurriculumVersion draft = findClassVersion(binding.getDraftVersionId(), classId);
            if (draft.getStatus() != CurriculumStatus.DRAFT) {
                throw new BusinessException(ErrorCode.CONFLICT, "Class draft curriculum is not editable");
            }
            return new CurriculumResolution(draft, binding, classId, true, SOURCE_CLASS_DRAFT);
        }

        if (binding.getPublishedVersionId() != null) {
            CurriculumVersion published = findClassVersion(binding.getPublishedVersionId(), classId);
            return new CurriculumResolution(published, binding, classId, true, SOURCE_CLASS_PUBLISHED);
        }

        CurriculumVersion inherited = findVersion(binding.getBaseMasterVersionId());
        return new CurriculumResolution(inherited, binding, classId, false, SOURCE_MASTER_INHERITED);
    }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveTrainerDraft(UUID courseId, UUID classId, UUID trainerId) {
        CurriculumResolution resolution = resolveTrainerEditing(courseId, classId, trainerId);
        if (resolution.version().getStatus() != CurriculumStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Initialize a class curriculum draft first");
        }
        return resolution;
    }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveTraineeLearning(UUID courseId, UUID classId, UUID studentId) {
        requireClassEnrollment(courseId, classId, studentId);
        return resolveClassEffectivePublished(courseId, classId);
    }

    // @Transactional(readOnly = true)
    // public CurriculumResolution resolveTraineeProgress(UUID courseId, UUID classId, UUID studentId) {
    //     requireClassEnrollment(courseId, classId, studentId);
    //     ClassCurriculumBinding binding = requireBinding(classId, courseId);

    //     if (binding.getDraftVersionId() != null) {
    //         CurriculumVersion draft = findClassVersion(binding.getDraftVersionId(), classId);
    //         if (draft.getStatus() == CurriculumStatus.DRAFT) {
    //             return new CurriculumResolution(draft, binding, classId, true, SOURCE_CLASS_DRAFT);
    //         }
    //     }

    //     return resolveClassEffectivePublished(courseId, classId);
    // }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveTraineeProgress(UUID courseId, UUID classId, UUID studentId) {
        return resolveTraineeLearning(courseId, classId, studentId);
    }

    @Transactional(readOnly = true)
    public CurriculumResolution resolveClassEffectivePublished(UUID courseId, UUID classId) {
        requireClassForCourse(courseId, classId);
        ClassCurriculumBinding binding = requireBinding(classId, courseId);

        if (binding.getPublishedVersionId() != null) {
            CurriculumVersion published = findClassVersion(binding.getPublishedVersionId(), classId);
            if (published.getStatus() == CurriculumStatus.PUBLISHED) {
                return new CurriculumResolution(published, binding, classId, true, SOURCE_CLASS_PUBLISHED);
            }
        }

        CurriculumVersion inherited = findVersion(binding.getBaseMasterVersionId());
        if (inherited.getStatus() != CurriculumStatus.PUBLISHED) {
            inherited = findPublishedMaster(courseId);
        }
        return new CurriculumResolution(inherited, binding, classId, false, SOURCE_MASTER_INHERITED);
    }

    @Transactional(readOnly = true)
    public CurriculumVersion resolveDraftInitializationSource(UUID courseId, UUID classId, UUID trainerId) {
        ClassOffering classOffering = requireClassForCourse(courseId, classId);
        requireTrainerOwner(classOffering, trainerId);
        ClassCurriculumBinding binding = requireBinding(classId, courseId);

        if (binding.getDraftVersionId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum draft already exists");
        }
        if (binding.getPublishedVersionId() != null) {
            return findClassVersion(binding.getPublishedVersionId(), classId);
        }
        return findVersion(binding.getBaseMasterVersionId());
    }

    private CurriculumVersion findPublishedMaster(UUID courseId) {
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        courseId,
                        CurriculumScope.MASTER,
                        CurriculumStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Published master curriculum not found"));
    }

    private CurriculumVersion findVersion(UUID versionId) {
        return curriculumVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Curriculum version not found"));
    }

    private CurriculumVersion findClassVersion(UUID versionId, UUID classId) {
        CurriculumVersion version = findVersion(versionId);
        if (!classId.equals(version.getClassId()) || version.getScope() != CurriculumScope.CLASS) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum binding is inconsistent");
        }
        return version;
    }

    private ClassCurriculumBinding requireBinding(UUID classId, UUID courseId) {
        return bindingRepository.findByClassIdAndCourseId(classId, courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class curriculum binding not found"));
    }

    private ClassOffering requireClassForCourse(UUID courseId, UUID classId) {
        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));
        if (!courseId.equals(classOffering.getCourseId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Class does not belong to this course");
        }
        return classOffering;
    }

    private void requireTrainerOwner(ClassOffering classOffering, UUID trainerId) {
        if (isAdministrator()) {
            return;
        }
        if (trainerId == null || !trainerId.equals(classOffering.getTrainerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Trainer is not assigned to this class");
        }
    }

    private boolean isAdministrator() {
        return authenticatedUserResolver.resolve()
                .map(user -> user.hasRole("ADMIN") || user.hasRole("TMO"))
                .orElse(false);
    }

    private void requireClassEnrollment(UUID courseId, UUID classId, UUID studentId) {
        requireClassForCourse(courseId, classId);
        ClassEnrollment enrollment = classEnrollmentRepository.findByClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "You are not enrolled in this class"));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE
                && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Class access is not active");
        }
    }
}
