package com.smartlearnly.backend.question.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseQuestionControllerSecurityTest {
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionService questionService;

    @Test
    void createQuestionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void createQuestionShouldRejectTrainee() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void createQuestionShouldRejectTmo() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void createQuestionShouldRejectTrainer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SME")
    void createQuestionShouldAllowSme() throws Exception {
        when(questionService.createForCourse(any(UUID.class), any(QuestionModel.CreateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText").value("What is 2 + 2?"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createQuestionShouldRejectAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/questions", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleBody()))
                .andExpect(status().isForbidden());
    }

    private String sampleBody() {
        return """
                {
                  "moduleId": "00000000-0000-0000-0000-000000000002",
                  "questionText": "What is 2 + 2?",
                  "questionType": "multiple_choice",
                  "bloomLevel": "remember",
                  "difficulty": 2,
                  "status": "draft",
                  "answers": [
                    {
                      "answerText": "4",
                      "correct": true,
                      "displayOrder": 1
                    },
                    {
                      "answerText": "5",
                      "correct": false,
                      "displayOrder": 2
                    }
                  ]
                }
                """;
    }

    private QuestionModel.Response sampleResponse() {
        UUID questionId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID answerId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Instant now = Instant.now();
        List<QuestionModel.AnswerResponse> answers = List.of(
                new QuestionModel.AnswerResponse(
                        answerId, answerId, "4", true, true, 1, 1, List.of()),
                new QuestionModel.AnswerResponse(
                        UUID.randomUUID(), UUID.randomUUID(), "5", false, false, 2, 2, List.of())
        );
        return new QuestionModel.Response(
                questionId,
                questionId,
                COURSE_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "What is 2 + 2?",
                "multiple_choice",
                "remember",
                (short) 2,
                null,
                null,
                null,
                List.<QuestionMediaAttachmentResponse>of(),
                false,
                null,
                "draft",
                2,
                answers,
                UUID.randomUUID(),
                null,
                null,
                now,
                now
        );
    }
}
