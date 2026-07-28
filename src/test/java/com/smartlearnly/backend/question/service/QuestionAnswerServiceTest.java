package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.dto.QuestionAnswerModel;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionAnswerServiceTest {

    @Mock
    private QuestionAnswerRepository repository;

    private QuestionAnswerService service;
    private UUID questionId;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        service = new QuestionAnswerService(repository);
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
    }

    @Test
    void create_returnsSavedAnswer() {
        QuestionAnswerModel.CreateRequest request = new QuestionAnswerModel.CreateRequest();
        request.setQuestionId(questionId);
        request.setAnswerText("Programming language");
        request.setIsCorrect(true);
        request.setOrderIndex(1);

        when(repository.save(any(QuestionAnswer.class))).thenAnswer(invocation -> {
            QuestionAnswer saved = invocation.getArgument(0);
            saved.setId(answerId);
            return saved;
        });

        QuestionAnswerModel.Response response = service.create(request);

        assertThat(response.getId()).isEqualTo(answerId);
        assertThat(response.getQuestionId()).isEqualTo(questionId);
        assertThat(response.getAnswerText()).isEqualTo("Programming language");
        assertThat(response.getIsCorrect()).isTrue();
        assertThat(response.getOrderIndex()).isEqualTo(1);
    }

    @Test
    void getAnswersByQuestion_mapsRepositoryResults() {
        QuestionAnswer answer = answer("A", true, 1);
        when(repository.findByQuestionId(questionId)).thenReturn(List.of(answer));

        List<QuestionAnswerModel.Response> responses = service.getAnswersByQuestion(questionId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAnswerText()).isEqualTo("A");
        assertThat(responses.get(0).getIsCorrect()).isTrue();
    }

    @Test
    void update_savesExistingAnswer_whenAnswerExists() {
        QuestionAnswer existing = answer("Old", false, 2);
        when(repository.findById(answerId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        QuestionAnswerModel.UpdateRequest request = new QuestionAnswerModel.UpdateRequest();
        request.setAnswerText("New");
        request.setIsCorrect(true);
        request.setOrderIndex(1);

        QuestionAnswerModel.Response response = service.update(answerId, request);

        assertThat(response.getAnswerText()).isEqualTo("New");
        assertThat(response.getIsCorrect()).isTrue();
        assertThat(response.getOrderIndex()).isEqualTo(1);
    }

    @Test
    void update_throwsEntityNotFound_whenAnswerDoesNotExist() {
        when(repository.findById(answerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(answerId, new QuestionAnswerModel.UpdateRequest()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Answer not found");

        verify(repository, never()).save(any());
    }

    @Test
    void delete_removesAnswer_whenAnswerExists() {
        when(repository.existsById(answerId)).thenReturn(true);

        service.delete(answerId);

        verify(repository).deleteById(answerId);
    }

    @Test
    void delete_throwsEntityNotFound_whenAnswerDoesNotExist() {
        when(repository.existsById(answerId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(answerId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Answer not found");

        verify(repository, never()).deleteById(any());
    }

    private QuestionAnswer answer(String text, boolean correct, int orderIndex) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(answerId);
        answer.setQuestionId(questionId);
        answer.setAnswerText(text);
        answer.setIsCorrect(correct);
        answer.setOrderIndex(orderIndex);
        return answer;
    }
}
