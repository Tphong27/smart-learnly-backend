package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumCustomizationState;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassCurriculumBindingProvisioningServiceTest {

    @Mock
    private ClassCurriculumBindingRepository bindingRepository;
    @Mock
    private CurriculumVersionRepository curriculumVersionRepository;

    @InjectMocks
    private ClassCurriculumBindingProvisioningService service;

    @Test
    void ensureBindingCreatesInheritedBindingFromPublishedMaster() {
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CurriculumVersion master = publishedMaster(courseId);

        when(bindingRepository.findByClassId(classId)).thenReturn(Optional.empty());
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.of(master));
        when(bindingRepository.save(any(ClassCurriculumBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClassCurriculumBinding binding = service.ensureBinding(classId, courseId);

        assertThat(binding.getClassId()).isEqualTo(classId);
        assertThat(binding.getCourseId()).isEqualTo(courseId);
        assertThat(binding.getBaseMasterVersionId()).isEqualTo(master.getId());
        assertThat(binding.getCustomizationState()).isEqualTo(CurriculumCustomizationState.INHERITED);
        assertThat(binding.getDraftVersionId()).isNull();
        assertThat(binding.getPublishedVersionId()).isNull();
    }

    @Test
    void ensureBindingReturnsExistingBindingWithoutCreatingAnother() {
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ClassCurriculumBinding existing = new ClassCurriculumBinding();
        existing.setClassId(classId);
        existing.setCourseId(courseId);

        when(bindingRepository.findByClassId(classId)).thenReturn(Optional.of(existing));

        assertThat(service.ensureBinding(classId, courseId)).isSameAs(existing);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void ensureBindingExplainsWhenPublishedMasterDoesNotExist() {
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(bindingRepository.findByClassId(classId)).thenReturn(Optional.empty());
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureBinding(classId, courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private CurriculumVersion publishedMaster(UUID courseId) {
        CurriculumVersion master = new CurriculumVersion();
        master.setId(UUID.randomUUID());
        master.setCourseId(courseId);
        master.setScope(CurriculumScope.MASTER);
        master.setStatus(CurriculumStatus.PUBLISHED);
        return master;
    }
}
