package com.smartlearnly.backend.question.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseQuestionControllerIntegrationTest {
    private static final UUID COURSE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MODULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionService questionService;

    @Test
    @WithMockUser(roles = "SME")
    void listQuestionsShouldReturnPagedQuestionsWithFilters() throws Exception {
        when(questionService.listByCourse(
                eq(COURSE_ID),
                eq(MODULE_ID),
                eq("oop"),
                eq("single_choice"),
                eq("draft"),
                eq(false),
                eq((short) 2),
                eq(0),
                eq(10)
        )).thenReturn(new PageResponse<>(List.of(sampleResponse("What is OOP?")), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .queryParam("moduleId", MODULE_ID.toString())
                        .queryParam("search", "oop")
                        .queryParam("type", "single_choice")
                        .queryParam("status", "draft")
                        .queryParam("includeArchived", "false")
                        .queryParam("difficulty", "2")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].questionText").value("What is OOP?"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    @WithMockUser(roles = "SME")
    void getQuestionShouldReturnDetailWithAnswersAndMedia() throws Exception {
        when(questionService.getInCourse(COURSE_ID, QUESTION_ID))
                .thenReturn(sampleResponse("What is integration testing?"));

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/questions/{questionId}", COURSE_ID, QUESTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionId").value(QUESTION_ID.toString()))
                .andExpect(jsonPath("$.data.answers.length()").value(2))
                .andExpect(jsonPath("$.data.mediaAttachments[0].mediaType").value("image"));
    }

    @Test
    @WithMockUser(roles = "SME")
    void createQuestionShouldRejectInvalidBodyBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "questionText": " ",
                                  "questionType": "",
                                  "answers": []
                                }
                                """.formatted(MODULE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(questionService, never()).createForCourse(any(UUID.class), any(QuestionModel.CreateRequest.class));
    }

    @Test
    @WithMockUser(roles = "SME")
    void updateQuestionShouldReturnUpdatedQuestion() throws Exception {
        when(questionService.updateInCourse(eq(COURSE_ID), eq(QUESTION_ID), any(QuestionModel.UpdateRequest.class)))
                .thenReturn(sampleResponse("Updated integration question"));

        mockMvc.perform(put("/api/v1/admin/courses/{courseId}/questions/{questionId}", COURSE_ID, QUESTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuestionBody("Updated integration question")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText").value("Updated integration question"));
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void updateQuestionShouldRejectTrainerRole() throws Exception {
        mockMvc.perform(put("/api/v1/admin/courses/{courseId}/questions/{questionId}", COURSE_ID, QUESTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuestionBody("Trainer should not update")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(questionService, never()).updateInCourse(any(UUID.class), any(UUID.class), any(QuestionModel.UpdateRequest.class));
    }

    @Test
    @WithMockUser(roles = "SME")
    void archiveQuestionShouldReturnSuccessResponse() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/courses/{courseId}/questions/{questionId}", COURSE_ID, QUESTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Question archived successfully"));

        verify(questionService).archiveInCourse(COURSE_ID, QUESTION_ID);
    }

    @Test
    @WithMockUser(roles = "SME")
    void importQuestionsShouldReturnBatchSummary() throws Exception {
        when(questionService.importBatchForCourse(eq(COURSE_ID), any(QuestionImportDtos.ImportBatchRequest.class)))
                .thenReturn(new QuestionImportDtos.ImportBatchResponse(1, 1, List.of(QUESTION_ID), List.of()));

        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions/import", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "importSource": "json_import",
                                  "rows": [
                                    {
                                      "rowNumber": 1,
                                      "moduleId": "%s",
                                      "questionText": "Imported question",
                                      "questionType": "single_choice",
                                      "options": ["A", "B"],
                                      "correctAnswer": "A",
                                      "difficulty": 1
                                    }
                                  ]
                                }
                                """.formatted(MODULE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requested").value(1))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.createdQuestionIds[0]").value(QUESTION_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "SME")
    void importQuestionsShouldRejectEmptyRowsBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions/import", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rows": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(questionService, never()).importBatchForCourse(any(UUID.class), any(QuestionImportDtos.ImportBatchRequest.class));
    }

    @Test
    @WithMockUser(roles = "TMO")
    void exportQuestionsShouldReturnCsvWithPlainTextAndEscaping() throws Exception {
        when(questionService.listByCourse(COURSE_ID, null, null, null, null, true, null, 0, 10_000))
                .thenReturn(new PageResponse<>(List.of(sampleResponse("<p>What is \"CSV\"?</p>")), 0, 10_000, 1, 1));

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/questions/export", COURSE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"course-questions.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id,module_id,type,status,question_text")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"What is \"\"CSV\"\"?\"")));
    }

    private String validQuestionBody(String questionText) {
        return """
                {
                  "moduleId": "%s",
                  "questionText": "%s",
                  "questionType": "single_choice",
                  "bloomLevel": "remember",
                  "difficulty": 2,
                  "status": "draft",
                  "answers": [
                    {
                      "answerText": "Answer A",
                      "correct": true,
                      "displayOrder": 1
                    },
                    {
                      "answerText": "Answer B",
                      "correct": false,
                      "displayOrder": 2
                    }
                  ]
                }
                """.formatted(MODULE_ID, questionText);
    }

    private QuestionModel.Response sampleResponse(String questionText) {
        Instant now = Instant.parse("2026-08-09T15:00:00Z");
        UUID firstAnswerId = UUID.fromString("10000000-0000-0000-0000-000000000004");
        UUID secondAnswerId = UUID.fromString("10000000-0000-0000-0000-000000000005");
        UUID attachmentId = UUID.fromString("10000000-0000-0000-0000-000000000006");
        return new QuestionModel.Response(
                QUESTION_ID,
                QUESTION_ID,
                COURSE_ID,
                MODULE_ID,
                questionText,
                "single_choice",
                "remember",
                (short) 2,
                "Explanation",
                "https://cdn.example.test/question.png",
                null,
                List.of(new QuestionMediaAttachmentResponse(
                        attachmentId,
                        attachmentId,
                        QUESTION_ID,
                        "image",
                        "https://cdn.example.test/question.png",
                        "questions/question.png",
                        "smart-learnly-test",
                        "image/png",
                        1200,
                        "question.png",
                        1,
                        "manual_upload",
                        now,
                        now
                )),
                false,
                null,
                "draft",
                2,
                List.of(
                        new QuestionModel.AnswerResponse(firstAnswerId, firstAnswerId, "Answer A", true, true, 1, 1, List.of()),
                        new QuestionModel.AnswerResponse(secondAnswerId, secondAnswerId, "Answer B", false, false, 2, 2, List.of())
                ),
                UUID.fromString("10000000-0000-0000-0000-000000000007"),
                null,
                null,
                now,
                now
        );
    }
}
