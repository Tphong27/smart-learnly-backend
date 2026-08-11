package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumCustomizationState;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassCurriculumBindingProvisioningService {

    private final ClassCurriculumBindingRepository bindingRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final ClassOfferingRepository classOfferingRepository;

    /**
     * Repairs classes created after the curriculum-versioning migration. A separate transaction is
     * required because curriculum resolution is frequently invoked from read-only transactions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClassCurriculumBinding ensureBinding(UUID classId, UUID courseId) {
        // Khóa hàng class trước thao tác check-then-insert để hai request Dashboard
        // đồng thời không cùng tạo binding và vi phạm unique(class_id).
        classOfferingRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));
        return bindingRepository.findByClassId(classId)
                .map(binding -> validateCourse(binding, courseId))
                .orElseGet(() -> createInheritedBinding(classId, courseId));
    }

    /** Tạo binding kế thừa từ master curriculum đã xuất bản gần nhất. */
    private ClassCurriculumBinding createInheritedBinding(UUID classId, UUID courseId) {
        CurriculumVersion publishedMaster = curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        courseId,
                        CurriculumScope.MASTER,
                        CurriculumStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Published master curriculum not found"));

        ClassCurriculumBinding binding = new ClassCurriculumBinding();
        binding.setClassId(classId);
        binding.setCourseId(courseId);
        binding.setBaseMasterVersionId(publishedMaster.getId());
        binding.setCustomizationState(CurriculumCustomizationState.INHERITED);
        return bindingRepository.save(binding);
    }

    /** Bảo đảm binding hiện hữu vẫn thuộc đúng khóa học của lớp. */
    private ClassCurriculumBinding validateCourse(ClassCurriculumBinding binding, UUID courseId) {
        if (!courseId.equals(binding.getCourseId())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Class curriculum binding is inconsistent");
        }
        return binding;
    }
}
