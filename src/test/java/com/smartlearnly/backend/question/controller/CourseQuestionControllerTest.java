package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CourseQuestionControllerTest {

    @Mock
    private QuestionService questionService;

    private CourseQuestionController controller;
    private UUID courseId;
    private UUID questionId;
    private UUID moduleId;

    @BeforeEach
    void setUp() {
        controller = new CourseQuestionController(questionService);
        courseId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
    }

    @Test
    void create_returnsCreatedResponseWithQuestionLocation() {
        QuestionModel.CreateRequest request = createRequest();
        QuestionModel.Response question = response("What is Java?", "draft");
        when(questionService.createForCourse(courseId, request)).thenReturn(question);

        var response = controller.create(courseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/api/v1/admin/courses/" + courseId + "/questions/" + questionId);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Question created successfully");
        assertThat(response.getBody().data()).isSameAs(question);
    }

    @Test
    void export_returnsCsvWithTemplateColumnsAnswersAndMedia() {
        QuestionModel.Response first = response("<p>Annotation&nbsp;nào \"Java\"?</p>", "draft");
        QuestionModel.Response second = new QuestionModel.Response(
                UUID.randomUUID(),
                UUID.randomUUID(),
                courseId,
                null,
                "True false statement",
                "true_false",
                null,
                null,
                "True statement explanation",
                null,
                null,
                List.of(
                        new QuestionMediaAttachmentResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                questionId,
                                "image",
                                "https://cdn.smartlearnly.test/question.png",
                                "questions/question.png",
                                "bucket",
                                "image/png",
                                1024,
                                "question.png",
                                1,
                                null,
                                null,
                                null
                        ),
                        new QuestionMediaAttachmentResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                questionId,
                                "audio",
                                "https://cdn.smartlearnly.test/question.mp3",
                                "questions/question.mp3",
                                "bucket",
                                "audio/mpeg",
                                2048,
                                "question.mp3",
                                1,
                                null,
                                null,
                                null
                        )
                ),
                false,
                null,
                "approved",
                2,
                List.of(
                        new QuestionModel.AnswerResponse(null, null, "True", true, true, 1, 1, List.of()),
                        new QuestionModel.AnswerResponse(null, null, "False", false, false, 2, 2, List.of())
                ),
                null,
                null,
                null,
                null,
                null);
        when(questionService.listByCourse(courseId, null, "java", null, null, true, null, 0, 10_000))
                .thenReturn(new PageResponse<>(List.of(first, second), 0, 10_000, 2, 1));

        var response = controller.export(courseId, null, "java", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("course-questions.csv");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getBody()).startsWith(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,status,Question text,Question type,Option A,Option B,Option C,Option D,Option E,Option F,Correct answer,Explanation,Module ID,Image files,Audio files\n");
        assertThat(csv).contains("\"Annotation nào \"\"Java\"\"?\"");
        assertThat(csv).doesNotContain("<p>").doesNotContain("&nbsp;");
        assertThat(csv).contains("\"Option A\",\"Option B\",\"\",\"\",\"\",\"\",\"A\",\"Explanation\"");
        assertThat(csv).contains("\"" + moduleId + "\",\"https://cdn.smartlearnly.test/question.png\",\"https://cdn.smartlearnly.test/question.mp3\"");
        assertThat(csv).contains("\"true_false\",\"True\",\"False\",\"\",\"\",\"\",\"\",\"True\",\"True statement explanation\"");
        verify(questionService).listByCourse(courseId, null, "java", null, null, true, null, 0, 10_000);
    }

    @Test
    void list_delegatesFiltersToQuestionService() {
        when(questionService.listByCourse(
                eq(courseId),
                eq(moduleId),
                eq("search"),
                eq("multiple_choice"),
                eq("draft"),
                eq(false),
                eq((short) 2),
                eq(1),
                eq(25)))
                .thenReturn(new PageResponse<>(List.of(), 1, 25, 0, 0));

        var response = controller.list(
                courseId,
                moduleId,
                "search",
                "multiple_choice",
                "draft",
                false,
                (short) 2,
                1,
                25);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Questions loaded successfully");
        assertThat(response.data().items()).isEmpty();
    }

    @Test
    void get_returnsQuestionFromService() {
        QuestionModel.Response question = response("What is Java?", "draft");
        when(questionService.getInCourse(courseId, questionId)).thenReturn(question);

        var response = controller.get(courseId, questionId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question loaded successfully");
        assertThat(response.data()).isSameAs(question);
    }

    @Test
    void update_delegatesToQuestionService() {
        QuestionModel.UpdateRequest request = updateRequest();
        QuestionModel.Response updated = response("Updated?", "pending_review");
        when(questionService.updateInCourse(courseId, questionId, request)).thenReturn(updated);

        var response = controller.update(courseId, questionId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(updated);
    }

    @Test
    void archive_returnsSuccessAfterServiceCall() {
        var response = controller.archive(courseId, questionId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Question archived successfully");
        verify(questionService).archiveInCourse(courseId, questionId);
    }

    @Test
    void importBatch_delegatesToQuestionService() {
        QuestionImportDtos.ImportBatchRequest request =
                new QuestionImportDtos.ImportBatchRequest(List.of(), "excel");
        QuestionImportDtos.ImportBatchResponse imported =
                new QuestionImportDtos.ImportBatchResponse(0, 0, List.of(), List.of());
        when(questionService.importBatchForCourse(courseId, request)).thenReturn(imported);

        var response = controller.importBatch(courseId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(imported);
    }

    private QuestionModel.CreateRequest createRequest() {
        return new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "multiple_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                List.of(new QuestionModel.AnswerRequest(null, null, "A", true, null, 1, null),
                        new QuestionModel.AnswerRequest(null, null, "B", false, null, 2, null)));
    }

    private QuestionModel.UpdateRequest updateRequest() {
        return new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated?",
                "multiple_choice",
                "understand",
                (short) 3,
                null,
                "pending_review",
                List.of(new QuestionModel.AnswerRequest(null, null, "A", true, null, 1, null),
                        new QuestionModel.AnswerRequest(null, null, "B", false, null, 2, null)));
    }

    private QuestionModel.Response response(String questionText, String status) {
        return new QuestionModel.Response(
                questionId,
                questionId,
                courseId,
                moduleId,
                questionText,
                "multiple_choice",
                "remember",
                (short) 2,
                "Explanation",
                null,
                null,
                List.of(
                        new QuestionMediaAttachmentResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                questionId,
                                "image",
                                "https://cdn.smartlearnly.test/question.png",
                                "questions/question.png",
                                "bucket",
                                "image/png",
                                1024,
                                "question.png",
                                1,
                                null,
                                null,
                                null
                        ),
                        new QuestionMediaAttachmentResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                questionId,
                                "audio",
                                "https://cdn.smartlearnly.test/question.mp3",
                                "questions/question.mp3",
                                "bucket",
                                "audio/mpeg",
                                2048,
                                "question.mp3",
                                1,
                                null,
                                null,
                                null
                        )
                ),
                false,
                null,
                status,
                2,
                List.of(
                        new QuestionModel.AnswerResponse(null, null, "Option A", true, true, 1, 1, List.of()),
                        new QuestionModel.AnswerResponse(null, null, "Option B", false, false, 2, 2, List.of())
                ),
                UUID.randomUUID(),
                null,
                null,
                null,
                null);
    }
}
