package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

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
    void list_returnsOrderedAttachments_whenQuestionIsReadable() {
        QuestionMediaAttachment image = attachment(QuestionMediaType.IMAGE, 1);
        QuestionMediaAttachment audio = attachment(QuestionMediaType.AUDIO, 1);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(image));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.AUDIO))
                .thenReturn(List.of(audio));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.VIDEO))
                .thenReturn(List.of());

        List<?> result = questionMediaService.list(questionId);

        assertThat(result).hasSize(2);
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void upload_savesImageAttachment_whenFileIsValid() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(fileStorageService.store(eq("question-media"), any(), eq("image/png"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/question.png",
                        "questions/question.png",
                        "question.png",
                        "image/png",
                        pngBytes().length));
        when(mediaAttachmentRepository.save(any(QuestionMediaAttachment.class))).thenAnswer(invocation -> {
            QuestionMediaAttachment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        QuestionMediaDtos.UploadResponse response = questionMediaService.upload(
                questionId,
                "image",
                List.of(new MockMultipartFile("files", "C:\\fake\\question.png", "image/png", pngBytes())));

        assertThat(response.questionId()).isEqualTo(questionId);
        assertThat(response.mediaAttachments()).hasSize(1);
        assertThat(response.mediaAttachments().get(0).mediaType()).isEqualTo("image");
        assertThat(response.mediaAttachments().get(0).fileName()).isEqualTo("question.png");
        verify(mediaAttachmentRepository).save(any(QuestionMediaAttachment.class));
    }

    @Test
    void upload_savesAudioAttachment_whenFileIsValid() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionAudioMaxSize()).thenReturn(DataSize.ofMegabytes(20));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.AUDIO))
                .thenReturn(1L);
        when(fileStorageService.store(eq("question-media"), any(), eq("audio/mpeg"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/question.mp3",
                        "questions/question.mp3",
                        "fallback.mp3",
                        "audio/mpeg",
                        mp3Bytes().length));
        when(mediaAttachmentRepository.save(any(QuestionMediaAttachment.class))).thenAnswer(invocation -> {
            QuestionMediaAttachment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        QuestionMediaDtos.UploadResponse response = questionMediaService.upload(
                questionId,
                "audio",
                List.of(new MockMultipartFile("files", " question.mp3 ", "audio/mpeg", mp3Bytes())));

        assertThat(response.mediaAttachments()).singleElement()
                .satisfies(media -> {
                    assertThat(media.mediaType()).isEqualTo("audio");
                    assertThat(media.displayOrder()).isEqualTo(2);
                    assertThat(media.fileName()).isEqualTo("question.mp3");
                });
        verify(fileStorageService).store(eq("question-media"), org.mockito.ArgumentMatchers.contains("/audios/"), eq("audio/mpeg"), any());
    }

    @Test
    void upload_savesVideoAttachment_whenFileIsValidAndUsesFallbackFileName() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionVideoMaxSize()).thenReturn(DataSize.ofMegabytes(100));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.VIDEO))
                .thenReturn(0L);
        when(fileStorageService.store(eq("question-media"), any(), eq("video/quicktime"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/question.mp4",
                        "questions/question.mp4",
                        "fallback.mp4",
                        "video/quicktime",
                        mp4Bytes().length));
        when(mediaAttachmentRepository.save(any(QuestionMediaAttachment.class))).thenAnswer(invocation -> {
            QuestionMediaAttachment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        QuestionMediaDtos.UploadResponse response = questionMediaService.upload(
                questionId,
                "video",
                List.of(new MockMultipartFile("files", " ", "video/mp4", mp4Bytes())));

        assertThat(response.mediaAttachments()).singleElement()
                .satisfies(media -> {
                    assertThat(media.mediaType()).isEqualTo("video");
                    assertThat(media.fileName()).isEqualTo("fallback.mp4");
                });
        verify(fileStorageService).store(eq("question-media"), org.mockito.ArgumentMatchers.contains("/videos/"), eq("video/quicktime"), any());
    }

    @Test
    void upload_throwsBusinessRuleViolation_whenImageLimitWouldBeExceeded() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.IMAGE))
                .thenReturn(5L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "image",
                List.of(new MockMultipartFile("files", "question.png", "image/png", pngBytes()))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
        verify(mediaAttachmentRepository, never()).save(any());
    }

    @Test
    void upload_throwsBusinessRuleViolation_whenAudioLimitWouldBeExceeded() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.AUDIO))
                .thenReturn(3L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "audio",
                List.of(new MockMultipartFile("files", "question.mp3", "audio/mpeg", mp3Bytes()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 3 audio files")
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void upload_throwsBusinessRuleViolation_whenVideoLimitWouldBeExceeded() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.VIDEO))
                .thenReturn(1L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "video",
                List.of(new MockMultipartFile("files", "question.mp4", "video/mp4", mp4Bytes()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 1 video")
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void upload_throwsInvalidRequest_whenFilesAreMissing() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one media file is required")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(mediaAttachmentRepository, never()).countByQuestionIdAndMediaType(any(), any());
    }

    @Test
    void upload_throwsPayloadTooLarge_whenFileExceedsMaxSize() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofBytes(1));

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "image",
                List.of(new MockMultipartFile("files", "question.png", "image/png", pngBytes()))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void upload_throwsInvalidRequest_whenFileCannotBeRead() throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getBytes()).thenThrow(new IOException("read failed"));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "image", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("could not be read")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void upload_throwsInvalidRequest_whenFileContentIsEmptyAfterRead() throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getBytes()).thenReturn(new byte[0]);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.AUDIO))
                .thenReturn(0L);
        when(storageProperties.getQuestionAudioMaxSize()).thenReturn(DataSize.ofMegabytes(20));

        assertThatThrownBy(() -> questionMediaService.upload(questionId, "audio", List.of(file)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Question audio file is required")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void upload_throwsUnsupportedMediaType_whenImageContentIsNotAnImage() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "image",
                List.of(new MockMultipartFile("files", "question.txt", "text/plain", "not image".getBytes()))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void upload_throwsUnsupportedMediaType_whenAudioContentIsNotAudio() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionAudioMaxSize()).thenReturn(DataSize.ofMegabytes(20));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.AUDIO))
                .thenReturn(0L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "audio",
                List.of(new MockMultipartFile("files", "question.txt", "text/plain", "not audio".getBytes()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MP3, M4A, or WAV")
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void upload_throwsUnsupportedMediaType_whenVideoContentIsNotVideo() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(storageProperties.getQuestionVideoMaxSize()).thenReturn(DataSize.ofMegabytes(100));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(questionId, QuestionMediaType.VIDEO))
                .thenReturn(0L);

        assertThatThrownBy(() -> questionMediaService.upload(
                questionId,
                "video",
                List.of(new MockMultipartFile("files", "question.txt", "text/plain", "not video".getBytes()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MP4, WebM")
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void reorder_updatesDisplayOrder_whenRequestContainsAllAttachments() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        QuestionMediaAttachment first = attachment(firstId, QuestionMediaType.IMAGE, 1);
        QuestionMediaAttachment second = attachment(secondId, QuestionMediaType.IMAGE, 2);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(second, first));

        var response = questionMediaService.reorder(
                questionId,
                new QuestionMediaDtos.ReorderRequest("image", List.of(secondId, firstId)));

        assertThat(response).hasSize(2);
        assertThat(second.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
        verify(mediaAttachmentRepository, times(2)).saveAll(any());
        verify(mediaAttachmentRepository, times(2)).flush();
    }

    @Test
    void reorder_throwsInvalidRequest_whenIdsAreDuplicated() {
        UUID attachmentId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionMediaService.reorder(
                questionId,
                new QuestionMediaDtos.ReorderRequest("image", List.of(attachmentId, attachmentId))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(mediaAttachmentRepository, never())
                .findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(any(), any());
    }

    @Test
    void reorder_throwsInvalidRequest_whenIdsAreMissing() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionMediaService.reorder(
                questionId,
                new QuestionMediaDtos.ReorderRequest("image", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Attachment IDs are required")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void reorder_throwsInvalidRequest_whenRequestDoesNotIncludeAllAttachments() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(
                        attachment(firstId, QuestionMediaType.IMAGE, 1),
                        attachment(secondId, QuestionMediaType.IMAGE, 2)));

        assertThatThrownBy(() -> questionMediaService.reorder(
                questionId,
                new QuestionMediaDtos.ReorderRequest("image", List.of(firstId))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("include all attachments")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(mediaAttachmentRepository, never()).saveAll(any());
    }

    @Test
    void reorder_throwsInvalidRequest_whenAttachmentDoesNotBelongToMediaType() {
        UUID firstId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(attachment(firstId, QuestionMediaType.IMAGE, 1)));

        assertThatThrownBy(() -> questionMediaService.reorder(
                questionId,
                new QuestionMediaDtos.ReorderRequest("image", List.of(foreignId))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(mediaAttachmentRepository, never()).saveAll(any());
    }

    @Test
    void delete_removesAttachmentAndNormalizesOrder_whenAttachmentExists() {
        UUID attachmentId = UUID.randomUUID();
        QuestionMediaAttachment deleted = attachment(attachmentId, QuestionMediaType.IMAGE, 1);
        QuestionMediaAttachment remaining = attachment(UUID.randomUUID(), QuestionMediaType.IMAGE, 2);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndId(questionId, attachmentId))
                .thenReturn(Optional.of(deleted));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(remaining));

        questionMediaService.delete(questionId, attachmentId);

        assertThat(remaining.getDisplayOrder()).isEqualTo(1);
        verify(mediaAttachmentRepository).delete(deleted);
        verify(mediaAttachmentRepository, times(2)).saveAll(any());
        verify(mediaAttachmentRepository, times(3)).flush();
    }

    @Test
    void delete_throwsNotFound_whenAttachmentDoesNotExist() {
        UUID attachmentId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(mediaAttachmentRepository.findByQuestionIdAndId(questionId, attachmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> questionMediaService.delete(questionId, attachmentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("attachment not found")
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(mediaAttachmentRepository, never()).delete(any());
    }

    @Test
    void list_throwsNotFound_whenQuestionDoesNotExist() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questionMediaService.list(questionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(courseAccessService, never()).requireReadableCourse(any());
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

    @Test
    void parseMediaType_throwsInvalidRequest_whenValueIsUnsupported() {
        assertThatThrownBy(() -> questionMediaService.parseMediaType("document"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void parseMediaType_acceptsHyphenatedAndBlankValues() {
        assertThat(questionMediaService.parseMediaType(" video ")).isEqualTo(QuestionMediaType.VIDEO);

        assertThatThrownBy(() -> questionMediaService.parseMediaType(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Media type is required")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void toResponse_returnsNullAndDefaultsNullableNumericFields() {
        assertThat(questionMediaService.toResponse(null)).isNull();
        QuestionMediaAttachment attachment = attachment(QuestionMediaType.IMAGE, 1);
        attachment.setFileSize(null);
        attachment.setDisplayOrder(null);

        var response = questionMediaService.toResponse(attachment);

        assertThat(response.size()).isZero();
        assertThat(response.displayOrder()).isZero();
    }

    private void denyCourseUpdate() {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"))
                .when(courseAccessService)
                .requireUpdatableCourse(courseId);
    }

    private QuestionMediaAttachment attachment(QuestionMediaType mediaType, int displayOrder) {
        return attachment(UUID.randomUUID(), mediaType, displayOrder);
    }

    private QuestionMediaAttachment attachment(UUID attachmentId, QuestionMediaType mediaType, int displayOrder) {
        QuestionMediaAttachment attachment = new QuestionMediaAttachment();
        attachment.setId(attachmentId);
        attachment.setQuestionId(questionId);
        attachment.setMediaType(mediaType);
        attachment.setMediaUrl("https://cdn.example.com/media-" + displayOrder);
        attachment.setObjectKey("questions/media-" + displayOrder);
        attachment.setBucket("question-media");
        attachment.setContentType(mediaType == QuestionMediaType.IMAGE ? "image/png" : "audio/mpeg");
        attachment.setFileSize(10L);
        attachment.setOriginalFileName("media-" + displayOrder);
        attachment.setDisplayOrder(displayOrder);
        attachment.setImportSource("manual");
        return attachment;
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    }

    private byte[] mp3Bytes() {
        return new byte[]{
                'I', 'D', '3', 3, 0, 0, 0, 0, 0, 10,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    }

    private byte[] mp4Bytes() {
        return new byte[]{
                0, 0, 0, 24, 'f', 't', 'y', 'p',
                'i', 's', 'o', 'm', 0, 0, 2, 0,
                'i', 's', 'o', 'm', 'i', 's', 'o', '2'
        };
    }
}
