package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.service.QuestionMediaService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class QuestionMediaControllerTest {

    @Mock
    private QuestionMediaService questionMediaService;

    private QuestionMediaController controller;
    private UUID questionId;
    private UUID attachmentId;

    @BeforeEach
    void setUp() {
        controller = new QuestionMediaController(questionMediaService);
        questionId = UUID.randomUUID();
        attachmentId = UUID.randomUUID();
    }

    @Test
    void list_returnsApiResponseFromService() {
        QuestionMediaAttachmentResponse attachment = attachment();
        when(questionMediaService.list(questionId)).thenReturn(List.of(attachment));

        var response = controller.list(questionId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question media loaded successfully");
        assertThat(response.data()).containsExactly(attachment);
    }

    @Test
    void upload_returnsUploadResponseFromService() {
        List<MultipartFile> files = List.of(new MockMultipartFile(
                "files",
                "question.png",
                "image/png",
                new byte[]{1}));
        QuestionMediaDtos.UploadResponse uploadResponse =
                new QuestionMediaDtos.UploadResponse(questionId, List.of(attachment()));
        when(questionMediaService.upload(questionId, "image", files)).thenReturn(uploadResponse);

        var response = controller.upload(questionId, "image", files);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question media uploaded successfully");
        assertThat(response.data()).isSameAs(uploadResponse);
    }

    @Test
    void reorder_returnsReorderedAttachmentsFromService() {
        QuestionMediaDtos.ReorderRequest request =
                new QuestionMediaDtos.ReorderRequest("image", List.of(attachmentId));
        QuestionMediaAttachmentResponse attachment = attachment();
        when(questionMediaService.reorder(questionId, request)).thenReturn(List.of(attachment));

        var response = controller.reorder(questionId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question media reordered successfully");
        assertThat(response.data()).containsExactly(attachment);
    }

    @Test
    void delete_returnsSuccessAfterServiceCall() {
        var response = controller.delete(questionId, attachmentId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question media removed successfully");
        verify(questionMediaService).delete(questionId, attachmentId);
    }

    private QuestionMediaAttachmentResponse attachment() {
        return new QuestionMediaAttachmentResponse(
                attachmentId,
                attachmentId,
                questionId,
                "image",
                "https://cdn.example.com/question.png",
                "questions/question.png",
                "question-media",
                "image/png",
                10,
                "question.png",
                1,
                "manual",
                Instant.now(),
                Instant.now());
    }
}
