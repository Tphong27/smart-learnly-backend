package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import com.smartlearnly.backend.test.definition.service.TestService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TrainerLessonQuestionService} covering the most complex
 * functions of the quiz creation flow: question update delegation, reorder
 * validation (empty/duplicate ids, default marks) and lazy default test title
 * generation. Pure Mockito/JUnit tests, no Spring context involved.
 */
@ExtendWith(MockitoExtension.class)
class TrainerLessonQuestionServiceUnitTest {

    @Mock
    private TrainerClassCurriculumService trainerClassCurriculumService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private TestService testService;
    @Mock
    private TestQuestionService testQuestionService;

    private TrainerLessonQuestionService service;

    private final UUID classId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TrainerLessonQuestionService(
                trainerClassCurriculumService,
                curriculumLessonRepository,
                testService,
                testQuestionService);
    }

    @Test
    void updateQuestionShouldDelegateWithLessonTestId() {
        UUID testId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        CurriculumLesson lesson = lesson(testId);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestQuestionModel.UpdateRequest request = new TestQuestionModel.UpdateRequest();
        request.setOrderIndex(3);
        request.setMarks(new BigDecimal("2.0"));
        TestQuestionModel.Response expected = new TestQuestionModel.Response();
        when(testQuestionService.updateTestQuestion(testId, questionId, request)).thenReturn(expected);

        TestQuestionModel.Response result = service.updateQuestion(classId, lessonId, questionId, request);

        assertThat(result).isSameAs(expected);
        verify(testQuestionService).updateTestQuestion(testId, questionId, request);
    }

    @Test
    void updateQuestionShouldRejectWhenLessonHasNoTest() {
        CurriculumLesson lesson = lesson(null);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);

        assertThatThrownBy(() -> service.updateQuestion(
                classId, lessonId, UUID.randomUUID(), new TestQuestionModel.UpdateRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(testQuestionService);
    }

    @Test
    void reorderQuestionsShouldRejectEmptyList() {
        CurriculumLesson lesson = lesson(UUID.randomUUID());
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);

        assertThatThrownBy(() -> service.reorderQuestions(classId, lessonId, new ReorderRequest(List.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(testQuestionService);
    }

    @Test
    void reorderQuestionsShouldRejectDuplicateIds() {
        CurriculumLesson lesson = lesson(UUID.randomUUID());
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        UUID q1 = UUID.randomUUID();

        assertThatThrownBy(() -> service.reorderQuestions(
                classId, lessonId, new ReorderRequest(List.of(q1, q1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(testQuestionService);
    }

    @Test
    void reorderQuestionsShouldDefaultMarksToOneWhenCurrentMarksNull() {
        UUID testId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        CurriculumLesson lesson = lesson(testId);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestQuestionModel.Response existing = new TestQuestionModel.Response();
        existing.setQuestionId(q1);
        existing.setMarks(null);
        when(testQuestionService.getQuestionsByTest(testId)).thenReturn(List.of(existing));

        service.reorderQuestions(classId, lessonId, new ReorderRequest(List.of(q1)));

        ArgumentCaptor<TestQuestionModel.UpdateRequest> captor =
                ArgumentCaptor.forClass(TestQuestionModel.UpdateRequest.class);
        verify(testQuestionService).updateTestQuestion(eq(testId), eq(q1), captor.capture());
        assertThat(captor.getValue().getOrderIndex()).isZero();
        assertThat(captor.getValue().getMarks()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void attachQuestionShouldTruncateOverlongDefaultTestTitleTo240Chars() {
        String longTitle = "A".repeat(300);
        CurriculumLesson lesson = lessonWithCourse(null, UUID.randomUUID(), longTitle);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestModel.Response created = new TestModel.Response();
        created.setId(UUID.randomUUID());
        when(testService.createTest(any(TestModel.CreateRequest.class))).thenReturn(created);
        when(testQuestionService.addQuestionToTest(any(TestQuestionModel.AddRequest.class)))
                .thenReturn(new TestQuestionModel.Response());

        TestQuestionModel.AddRequest request = new TestQuestionModel.AddRequest();
        request.setQuestionId(UUID.randomUUID());
        service.attachQuestion(classId, lessonId, request);

        ArgumentCaptor<TestModel.CreateRequest> createCaptor = ArgumentCaptor.forClass(TestModel.CreateRequest.class);
        verify(testService).createTest(createCaptor.capture());
        assertThat(createCaptor.getValue().getTitle()).hasSize(240);
        assertThat(createCaptor.getValue().getTitle()).startsWith("A");
    }

    @Test
    void attachQuestionShouldUseQuizTitleWhenLessonTitleBlank() {
        CurriculumLesson lesson = lessonWithCourse(null, UUID.randomUUID(), "  ");
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestModel.Response created = new TestModel.Response();
        created.setId(UUID.randomUUID());
        when(testService.createTest(any(TestModel.CreateRequest.class))).thenReturn(created);
        when(testQuestionService.addQuestionToTest(any(TestQuestionModel.AddRequest.class)))
                .thenReturn(new TestQuestionModel.Response());

        TestQuestionModel.AddRequest request = new TestQuestionModel.AddRequest();
        request.setQuestionId(UUID.randomUUID());
        service.attachQuestion(classId, lessonId, request);

        ArgumentCaptor<TestModel.CreateRequest> createCaptor = ArgumentCaptor.forClass(TestModel.CreateRequest.class);
        verify(testService).createTest(createCaptor.capture());
        assertThat(createCaptor.getValue().getTitle()).isEqualTo("Quiz");
    }

    private CurriculumLesson lesson(UUID testId) {
        return lessonWithCourse(testId, UUID.randomUUID(), "Sample lesson");
    }

    private CurriculumLesson lessonWithCourse(UUID testId, UUID courseId, String title) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setTitle(title);
        lesson.setTestId(testId);
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        lesson.setSection(section);
        return lesson;
    }
}
