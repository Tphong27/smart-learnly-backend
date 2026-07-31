package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.dto.QuestionAnswerModel;
import com.smartlearnly.backend.question.service.QuestionAnswerService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QuestionAnswerControllerTest {

    @Mock
    private QuestionAnswerService questionAnswerService;

    private QuestionAnswerController controller;
    private UUID questionId;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        controller = new QuestionAnswerController(questionAnswerService);
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
    }

    @Test
    void create_returnsCreatedResponse() {
        QuestionAnswerModel.CreateRequest request = new QuestionAnswerModel.CreateRequest();
        request.setQuestionId(questionId);
        request.setAnswerText("A");
        request.setIsCorrect(true);
        request.setOrderIndex(1);
        QuestionAnswerModel.Response created = response();
        when(questionAnswerService.create(request)).thenReturn(created);

        var response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
    }

    @Test
    void getAnswersByQuestion_returnsServiceResponses() {
        QuestionAnswerModel.Response answer = response();
        when(questionAnswerService.getAnswersByQuestion(questionId)).thenReturn(List.of(answer));

        var response = controller.getAnswersByQuestion(questionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(answer);
    }

    @Test
    void update_returnsUpdatedAnswer() {
        QuestionAnswerModel.UpdateRequest request = new QuestionAnswerModel.UpdateRequest();
        request.setAnswerText("Updated");
        request.setIsCorrect(false);
        request.setOrderIndex(2);
        QuestionAnswerModel.Response updated = response();
        when(questionAnswerService.update(answerId, request)).thenReturn(updated);

        var response = controller.update(answerId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(updated);
    }

    @Test
    void delete_returnsNoContentAfterServiceCall() {
        var response = controller.delete(answerId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(questionAnswerService).delete(answerId);
    }

    private QuestionAnswerModel.Response response() {
        QuestionAnswerModel.Response response = new QuestionAnswerModel.Response();
        response.setId(answerId);
        response.setQuestionId(questionId);
        response.setAnswerText("A");
        response.setIsCorrect(true);
        response.setOrderIndex(1);
        return response;
    }
}
