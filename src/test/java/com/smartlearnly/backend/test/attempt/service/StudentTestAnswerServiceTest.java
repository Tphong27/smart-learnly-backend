package com.smartlearnly.backend.test.attempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.test.attempt.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentTestAnswerServiceTest {

    @Mock private StudentTestAnswerRepository repository;
    @Mock private TestAttemptRepository attemptRepository;
    @Mock private QuestionAnswerRepository questionAnswerRepository;

    @InjectMocks private StudentTestAnswerService service;

    private UUID attemptId;
    private UUID questionId;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        attemptId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
    }

    @Test
    void saveStudentAnswer_createsNewAnswer() {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(attemptId);
        attempt.setEndTime(Instant.now().plusSeconds(60));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        StudentTestAnswer saved = new StudentTestAnswer();
        saved.setId(UUID.randomUUID());
        saved.setAttemptId(attemptId);
        saved.setQuestionId(questionId);
        saved.setSelectedAnswerId(answerId);
        when(repository.findByAttemptIdAndQuestionId(attemptId, questionId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            StudentTestAnswer a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        StudentTestAnswerModel.SaveRequest request = new StudentTestAnswerModel.SaveRequest();
        request.setAttemptId(attemptId);
        request.setQuestionId(questionId);
        request.setSelectedAnswerId(answerId);

        StudentTestAnswerModel.Response response = service.saveStudentAnswer(request);

        assertThat(response).isNotNull();
        assertThat(response.getAttemptId()).isEqualTo(attemptId);
        assertThat(response.getQuestionId()).isEqualTo(questionId);
        verify(repository).save(any(StudentTestAnswer.class));
    }

    @Test
    void saveStudentAnswer_updatesExistingAnswer() {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(attemptId);
        attempt.setEndTime(Instant.now().plusSeconds(60));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        UUID newAnswerId = UUID.randomUUID();
        StudentTestAnswer existing = new StudentTestAnswer();
        existing.setId(UUID.randomUUID());
        existing.setAttemptId(attemptId);
        existing.setQuestionId(questionId);
        existing.setSelectedAnswerId(answerId);

        when(repository.findByAttemptIdAndQuestionId(attemptId, questionId))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudentTestAnswerModel.SaveRequest request = new StudentTestAnswerModel.SaveRequest();
        request.setAttemptId(attemptId);
        request.setQuestionId(questionId);
        request.setSelectedAnswerId(newAnswerId);

        StudentTestAnswerModel.Response response = service.saveStudentAnswer(request);

        assertThat(response.getSelectedAnswerId()).isEqualTo(newAnswerId);
    }

    @Test
    void saveStudentAnswer_throwsWhenAttemptNotFound() {
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.empty());

        StudentTestAnswerModel.SaveRequest request = new StudentTestAnswerModel.SaveRequest();
        request.setAttemptId(attemptId);
        request.setQuestionId(questionId);

        assertThatThrownBy(() -> service.saveStudentAnswer(request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void saveStudentAnswer_throwsWhenAttemptExpired() {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(attemptId);
        attempt.setEndTime(Instant.now().minusSeconds(1));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        StudentTestAnswerModel.SaveRequest request = new StudentTestAnswerModel.SaveRequest();
        request.setAttemptId(attemptId);
        request.setQuestionId(questionId);

        assertThatThrownBy(() -> service.saveStudentAnswer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void saveStudentAnswer_savesEssayAnswer() {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(attemptId);
        attempt.setEndTime(Instant.now().plusSeconds(60));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        when(repository.findByAttemptIdAndQuestionId(attemptId, questionId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String essayText = "This is my essay answer.";
        StudentTestAnswerModel.SaveRequest request = new StudentTestAnswerModel.SaveRequest();
        request.setAttemptId(attemptId);
        request.setQuestionId(questionId);
        request.setEssayAnswer(essayText);

        StudentTestAnswerModel.Response response = service.saveStudentAnswer(request);

        assertThat(response.getEssayAnswer()).isEqualTo(essayText);
    }

    @Test
    void gradeStudentAnswer_updatesGradingFields() {
        UUID answerId = UUID.randomUUID();
        StudentTestAnswer entity = new StudentTestAnswer();
        entity.setId(answerId);
        entity.setAttemptId(attemptId);
        entity.setQuestionId(questionId);
        when(repository.findById(answerId)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudentTestAnswerModel.GradeRequest request = new StudentTestAnswerModel.GradeRequest();
        request.setIsCorrect(true);
        request.setScoreAwarded(BigDecimal.valueOf(10.0));
        request.setIssueReported("none");

        StudentTestAnswerModel.Response response = service.gradeStudentAnswer(answerId, request);

        assertThat(response.getIsCorrect()).isTrue();
        assertThat(response.getScoreAwarded()).isEqualTo(BigDecimal.valueOf(10.0));
        assertThat(response.getIssueReported()).isEqualTo("none");
    }

    @Test
    void gradeStudentAnswer_throwsWhenAnswerNotFound() {
        UUID answerId = UUID.randomUUID();
        when(repository.findById(answerId)).thenReturn(Optional.empty());

        StudentTestAnswerModel.GradeRequest request = new StudentTestAnswerModel.GradeRequest();

        assertThatThrownBy(() -> service.gradeStudentAnswer(answerId, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAnswersByAttempt_returnsAllAnswers() {
        StudentTestAnswer answer1 = new StudentTestAnswer();
        answer1.setId(UUID.randomUUID());
        answer1.setAttemptId(attemptId);
        answer1.setQuestionId(UUID.randomUUID());
        answer1.setSelectedAnswerId(UUID.randomUUID());

        StudentTestAnswer answer2 = new StudentTestAnswer();
        answer2.setId(UUID.randomUUID());
        answer2.setAttemptId(attemptId);
        answer2.setQuestionId(UUID.randomUUID());

        when(repository.findByAttemptId(attemptId)).thenReturn(List.of(answer1, answer2));

        List<StudentTestAnswerModel.Response> responses = service.getAnswersByAttempt(attemptId);

        assertThat(responses).hasSize(2);
    }

    @Test
    void getAnswersByAttempt_returnsEmptyListWhenNoAnswers() {
        when(repository.findByAttemptId(attemptId)).thenReturn(List.of());

        List<StudentTestAnswerModel.Response> responses = service.getAnswersByAttempt(attemptId);

        assertThat(responses).isEmpty();
    }
}
