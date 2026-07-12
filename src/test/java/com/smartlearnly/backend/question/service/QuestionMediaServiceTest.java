package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
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
    private QuestionBankRepository questionBankRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StorageProperties storageProperties;

    private QuestionMediaService questionMediaService;

    private UUID questionId;
    private UUID bankId;
    private Question question;
    private QuestionBank archivedBank;

    @BeforeEach
    void setUp() {
        questionMediaService = new QuestionMediaService(
                questionRepository,
                mediaAttachmentRepository,
                questionBankRepository,
                fileStorageService,
                storageProperties
        );
        questionId = UUID.randomUUID();
        bankId = UUID.randomUUID();
        question = new Question();
        question.setId(questionId);
        question.setQuestionBankId(bankId);
        question.setStatus(QuestionStatus.DRAFT);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);

        archivedBank = new QuestionBank();
        archivedBank.setId(bankId);
        archivedBank.setStatus("archived");
    }

    @Test
    void upload_whenBankArchived_throwsBusinessRule() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(archivedBank));

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(mediaAttachmentRepository, never()).save(any());
    }

    @Test
    void delete_whenBankArchived_throwsBusinessRule() {
        UUID attachmentId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(archivedBank));

        assertThatThrownBy(() -> questionMediaService.delete(questionId, attachmentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(mediaAttachmentRepository, never()).delete(any());
    }

    @Test
    void reorder_whenBankArchived_throwsBusinessRule() {
        UUID attachmentId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(archivedBank));

        QuestionMediaDtos.ReorderRequest request = new QuestionMediaDtos.ReorderRequest(
                "image", List.of(attachmentId)
        );

        assertThatThrownBy(() -> questionMediaService.reorder(questionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(mediaAttachmentRepository, never()).save(any());
    }

    @Test
    void upload_whenQuestionArchived_throwsBusinessRule_evenIfBankActive() {
        QuestionBank activeBank = new QuestionBank();
        activeBank.setId(bankId);
        activeBank.setStatus("approved");
        Question archivedQuestion = new Question();
        archivedQuestion.setId(questionId);
        archivedQuestion.setQuestionBankId(bankId);
        archivedQuestion.setStatus(QuestionStatus.ARCHIVED);
        archivedQuestion.setQuestionType(QuestionType.MULTIPLE_CHOICE);

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(archivedQuestion));

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(mediaAttachmentRepository, never()).save(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}