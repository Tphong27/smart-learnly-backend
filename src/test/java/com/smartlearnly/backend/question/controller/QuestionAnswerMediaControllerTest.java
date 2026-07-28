package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.service.QuestionAnswerMediaService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class QuestionAnswerMediaControllerTest {

    @Mock
    private QuestionAnswerMediaService answerMediaService;

    private QuestionAnswerMediaController controller;
    private UUID questionId;
    private UUID answerId;
    private UUID attachmentId;

    @BeforeEach
    void setUp() {
        controller = new QuestionAnswerMediaController(answerMediaService);
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        attachmentId = UUID.randomUUID();
    }

    @Test
    void list_returnsAnswerMediaFromService() {
        QuestionAnswerMediaResponse media = media();
        when(answerMediaService.list(questionId, answerId)).thenReturn(List.of(media));

        var response = controller.list(questionId, answerId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Answer media loaded successfully");
        assertThat(response.data()).containsExactly(media);
    }

    @Test
    void upload_returnsUploadedAnswerMedia() {
        MockMultipartFile file = new MockMultipartFile("file", "answer.png", "image/png", new byte[]{1});
        QuestionAnswerMediaResponse media = media();
        when(answerMediaService.upload(questionId, answerId, "image", file)).thenReturn(media);

        var response = controller.upload(questionId, answerId, "image", file);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Answer media uploaded successfully");
        assertThat(response.data()).isSameAs(media);
    }

    @Test
    void delete_returnsSuccessAfterServiceCall() {
        var response = controller.delete(questionId, answerId, attachmentId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Answer media removed successfully");
        verify(answerMediaService).delete(questionId, answerId, attachmentId);
    }

    private QuestionAnswerMediaResponse media() {
        return new QuestionAnswerMediaResponse(
                attachmentId,
                answerId,
                "image",
                "https://cdn.example.com/answer.png",
                "answers/answer.png",
                "question-media",
                "image/png",
                10,
                "answer.png",
                1,
                "manual",
                Instant.now(),
                Instant.now());
    }
}
