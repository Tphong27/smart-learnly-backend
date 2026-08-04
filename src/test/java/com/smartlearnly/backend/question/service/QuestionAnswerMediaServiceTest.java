package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class QuestionAnswerMediaServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository answerRepository;
    @Mock
    private QuestionAnswerMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StorageProperties storageProperties;

    private QuestionAnswerMediaService service;
    private UUID courseId;
    private UUID questionId;
    private UUID answerId;
    private UUID attachmentId;
    private Question question;
    private QuestionAnswer answer;

    @BeforeEach
    void setUp() {
        service = new QuestionAnswerMediaService(
                questionRepository,
                answerRepository,
                mediaAttachmentRepository,
                courseAccessService,
                fileStorageService,
                storageProperties
        );
        courseId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        attachmentId = UUID.randomUUID();

        question = new Question();
        question.setId(questionId);
        question.setCourseId(courseId);
        question.setStatus(QuestionStatus.DRAFT);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);

        answer = new QuestionAnswer();
        answer.setId(answerId);
        answer.setQuestionId(questionId);
        answer.setAnswerText("A");
        answer.setIsCorrect(true);
        answer.setOrderIndex(1);
    }

    @Test
    void list_returnsOrderedAnswerMedia_whenAnswerIsEditable() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(attachment(QuestionMediaType.IMAGE)));
        when(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.AUDIO))
                .thenReturn(List.of());
        when(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.VIDEO))
                .thenReturn(List.of());

        List<QuestionAnswerMediaResponse> response = service.list(questionId, answerId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).answerId()).isEqualTo(answerId);
        verify(courseAccessService).requireUpdatableCourse(courseId);
    }

    @Test
    void upload_savesAnswerImage_whenFileIsValid() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(mediaAttachmentRepository.countByAnswerIdAndMediaType(answerId, QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(mediaAttachmentRepository.findByAnswerIdAndMediaType(answerId, QuestionMediaType.IMAGE))
                .thenReturn(Optional.empty());
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(fileStorageService.store(eq("question-media"), any(), eq("image/png"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/answer.png",
                        "answers/answer.png",
                        "answer.png",
                        "image/png",
                        pngBytes().length));
        when(mediaAttachmentRepository.save(any(QuestionAnswerMediaAttachment.class))).thenAnswer(invocation -> {
            QuestionAnswerMediaAttachment saved = invocation.getArgument(0);
            saved.setId(attachmentId);
            return saved;
        });

        QuestionAnswerMediaResponse response = service.upload(
                questionId,
                answerId,
                "image",
                new MockMultipartFile("file", "answer.png", "image/png", pngBytes()));

        assertThat(response.attachmentId()).isEqualTo(attachmentId);
        assertThat(response.answerId()).isEqualTo(answerId);
        assertThat(response.mediaType()).isEqualTo("image");
        assertThat(response.displayOrder()).isEqualTo(1);
    }

    @Test
    void upload_throwsInvalidRequest_whenFileIsMissing() {
        assertThatThrownBy(() -> service.upload(questionId, answerId, "image", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(questionRepository, never()).findById(any());
    }

    @Test
    void upload_throwsBusinessRuleViolation_whenAnswerAlreadyHasImage() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(mediaAttachmentRepository.countByAnswerIdAndMediaType(answerId, QuestionMediaType.IMAGE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.upload(
                questionId,
                answerId,
                "image",
                new MockMultipartFile("file", "answer.png", "image/png", pngBytes())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void upload_throwsInvalidRequest_whenAnswerDoesNotBelongToQuestion() {
        QuestionAnswer otherAnswer = new QuestionAnswer();
        otherAnswer.setId(answerId);
        otherAnswer.setQuestionId(UUID.randomUUID());
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(otherAnswer));

        assertThatThrownBy(() -> service.upload(
                questionId,
                answerId,
                "image",
                new MockMultipartFile("file", "answer.png", "image/png", pngBytes())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(mediaAttachmentRepository, never()).save(any());
    }

    @Test
    void upload_throwsBusinessRuleViolation_whenQuestionIsArchived() {
        question.setStatus(QuestionStatus.ARCHIVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.upload(
                questionId,
                answerId,
                "image",
                new MockMultipartFile("file", "answer.png", "image/png", pngBytes())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        verify(courseAccessService, never()).requireUpdatableCourse(any());
        verify(answerRepository, never()).findById(any());
    }

    @Test
    void delete_removesAttachment_whenAttachmentExists() {
        QuestionAnswerMediaAttachment attachment = attachment(QuestionMediaType.IMAGE);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(mediaAttachmentRepository.findByAnswerIdAndId(answerId, attachmentId))
                .thenReturn(Optional.of(attachment));

        service.delete(questionId, answerId, attachmentId);

        verify(mediaAttachmentRepository).delete(attachment);
        verify(mediaAttachmentRepository).flush();
    }

    @Test
    void parseMediaType_throwsInvalidRequest_whenValueIsMissing() {
        assertThatThrownBy(() -> service.parseMediaType(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private QuestionAnswerMediaAttachment attachment(QuestionMediaType mediaType) {
        QuestionAnswerMediaAttachment attachment = new QuestionAnswerMediaAttachment();
        attachment.setId(attachmentId);
        attachment.setAnswerId(answerId);
        attachment.setMediaType(mediaType);
        attachment.setMediaUrl("https://cdn.example.com/answer-media");
        attachment.setObjectKey("answers/media");
        attachment.setBucket("question-media");
        attachment.setContentType(mediaType == QuestionMediaType.IMAGE ? "image/png" : "audio/mpeg");
        attachment.setFileSize(10L);
        attachment.setOriginalFileName("answer-media");
        attachment.setDisplayOrder(1);
        attachment.setImportSource("manual");
        return attachment;
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    }
}
