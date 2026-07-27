package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class QuestionMediaServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StorageProperties storageProperties;

    private QuestionMediaService questionMediaService;

    private UUID questionId;
    private UUID courseId;
    private Question question;

    @BeforeEach
    void setUp() {
        questionMediaService = new QuestionMediaService(
                questionRepository,
                mediaAttachmentRepository,
                courseAccessService,
                fileStorageService,
                storageProperties
        );
        questionId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        question = new Question();
        question.setId(questionId);
        question.setCourseId(courseId);
        question.setStatus(QuestionStatus.DRAFT);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
    }

    @Test
    void upload_whenCourseAccessDenied_throwsNotFound() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        denyCourseUpdate();

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(mediaAttachmentRepository, never()).save(any());
    }

    @Test
    void delete_whenCourseAccessDenied_doesNotReadAttachment() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        denyCourseUpdate();

        assertThatThrownBy(() -> questionMediaService.delete(questionId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(mediaAttachmentRepository, never()).findByQuestionIdAndId(any(), any());
    }

    @Test
    void reorder_whenCourseAccessDenied_doesNotReadAttachments() {
        UUID attachmentId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        denyCourseUpdate();

        QuestionMediaDtos.ReorderRequest request = new QuestionMediaDtos.ReorderRequest(
                "image", List.of(attachmentId)
        );

        assertThatThrownBy(() -> questionMediaService.reorder(questionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(mediaAttachmentRepository, never())
                .findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(any(), any());
    }

    @Test
    void upload_whenQuestionArchived_throwsBusinessRuleBeforeCourseAccess() {
        Question archivedQuestion = new Question();
        archivedQuestion.setId(questionId);
        archivedQuestion.setCourseId(courseId);
        archivedQuestion.setStatus(QuestionStatus.ARCHIVED);
        archivedQuestion.setQuestionType(QuestionType.MULTIPLE_CHOICE);

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(archivedQuestion));

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(courseAccessService, never()).requireUpdatableCourse(any());
        verify(mediaAttachmentRepository, never()).save(any());
    }

    private void denyCourseUpdate() {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"))
                .when(courseAccessService)
                .requireUpdatableCourse(courseId);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
