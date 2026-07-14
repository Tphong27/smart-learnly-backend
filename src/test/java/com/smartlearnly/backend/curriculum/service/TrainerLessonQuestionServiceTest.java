package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.test.dto.TestModel;
import com.smartlearnly.backend.test.dto.TestQuestionModel;
import com.smartlearnly.backend.test.service.TestQuestionService;
import com.smartlearnly.backend.test.service.TestService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainerLessonQuestionServiceTest {
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
    void listQuestionsShouldReturnEmptyWhenLessonHasNoTest() {
        CurriculumLesson lesson = lesson(null);
        when(trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId)).thenReturn(lesson);

        List<TestQuestionModel.Response> result = service.listQuestions(classId, lessonId);

        assertThat(result).isEmpty();
        verifyNoInteractions(testQuestionService);
    }

    @Test
    void listQuestionsShouldDelegateWhenLessonHasTest() {
        UUID testId = UUID.randomUUID();
        CurriculumLesson lesson = lesson(testId);
        when(trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId)).thenReturn(lesson);
        TestQuestionModel.Response response = new TestQuestionModel.Response();
        when(testQuestionService.getQuestionsByTest(testId)).thenReturn(List.of(response));

        List<TestQuestionModel.Response> result = service.listQuestions(classId, lessonId);

        assertThat(result).containsExactly(response);
    }

    @Test
    void attachQuestionShouldLazyCreateTestAndDelegate() {
        UUID courseId = UUID.randomUUID();
        UUID createdTestId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        CurriculumLesson lesson = lessonWithCourse(null, courseId);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestModel.Response createdTest = new TestModel.Response();
        createdTest.setId(createdTestId);
        when(testService.createTest(any(TestModel.CreateRequest.class))).thenReturn(createdTest);
        TestQuestionModel.Response added = new TestQuestionModel.Response();
        when(testQuestionService.addQuestionToTest(any(TestQuestionModel.AddRequest.class))).thenReturn(added);

        TestQuestionModel.AddRequest request = new TestQuestionModel.AddRequest();
        request.setQuestionId(questionId);
        request.setOrderIndex(0);

        TestQuestionModel.Response result = service.attachQuestion(classId, lessonId, request);

        assertThat(result).isSameAs(added);
        assertThat(lesson.getTestId()).isEqualTo(createdTestId);
        verify(curriculumLessonRepository).save(lesson);

        ArgumentCaptor<TestModel.CreateRequest> createCaptor = ArgumentCaptor.forClass(TestModel.CreateRequest.class);
        verify(testService).createTest(createCaptor.capture());
        assertThat(createCaptor.getValue().getCourseId()).isEqualTo(courseId);
        assertThat(createCaptor.getValue().getTitle()).isEqualTo("Sample lesson");

        ArgumentCaptor<TestQuestionModel.AddRequest> addCaptor = ArgumentCaptor.forClass(TestQuestionModel.AddRequest.class);
        verify(testQuestionService).addQuestionToTest(addCaptor.capture());
        assertThat(addCaptor.getValue().getTestId()).isEqualTo(createdTestId);
        assertThat(addCaptor.getValue().getQuestionId()).isEqualTo(questionId);
        assertThat(addCaptor.getValue().getMarks()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void attachQuestionShouldRejectMissingQuestionId() {
        CurriculumLesson lesson = lesson(UUID.randomUUID());
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);

        assertThatThrownBy(() -> service.attachQuestion(classId, lessonId, new TestQuestionModel.AddRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(testService, testQuestionService);
    }

    @Test
    void attachQuestionShouldPropagateOwnershipFailure() {
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Not the trainer"));

        TestQuestionModel.AddRequest request = new TestQuestionModel.AddRequest();
        request.setQuestionId(UUID.randomUUID());

        assertThatThrownBy(() -> service.attachQuestion(classId, lessonId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(testService, testQuestionService, curriculumLessonRepository);
    }

    @Test
    void detachQuestionShouldRejectWhenLessonHasNoTest() {
        CurriculumLesson lesson = lesson(null);
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);

        assertThatThrownBy(() -> service.detachQuestion(classId, lessonId, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verifyNoInteractions(testQuestionService);
    }

    @Test
    void reorderQuestionsShouldApplyOrderThroughUpdateCalls() {
        UUID testId = UUID.randomUUID();
        CurriculumLesson lesson = lesson(testId);
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestQuestionModel.Response first = new TestQuestionModel.Response();
        first.setQuestionId(q1);
        first.setMarks(new BigDecimal("1.5"));
        TestQuestionModel.Response second = new TestQuestionModel.Response();
        second.setQuestionId(q2);
        second.setMarks(new BigDecimal("2"));
        when(testQuestionService.getQuestionsByTest(testId)).thenReturn(List.of(first, second));

        service.reorderQuestions(classId, lessonId, new ReorderRequest(List.of(q2, q1)));

        ArgumentCaptor<TestQuestionModel.UpdateRequest> captor = ArgumentCaptor.forClass(TestQuestionModel.UpdateRequest.class);
        verify(testQuestionService).updateTestQuestion(eq(testId), eq(q2), captor.capture());
        verify(testQuestionService).updateTestQuestion(eq(testId), eq(q1), captor.capture());
        List<TestQuestionModel.UpdateRequest> captured = captor.getAllValues();
        assertThat(captured.get(0).getOrderIndex()).isZero();
        assertThat(captured.get(1).getOrderIndex()).isEqualTo(1);
        // Verify final refresh call
        verify(testQuestionService, times(2)).getQuestionsByTest(testId);
    }

    @Test
    void reorderQuestionsShouldRejectMismatch() {
        UUID testId = UUID.randomUUID();
        CurriculumLesson lesson = lesson(testId);
        UUID q1 = UUID.randomUUID();
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        TestQuestionModel.Response first = new TestQuestionModel.Response();
        first.setQuestionId(q1);
        first.setMarks(BigDecimal.ONE);
        when(testQuestionService.getQuestionsByTest(testId)).thenReturn(List.of(first));

        UUID unrelated = UUID.randomUUID();
        assertThatThrownBy(() -> service.reorderQuestions(classId, lessonId, new ReorderRequest(List.of(unrelated))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(testQuestionService, never()).updateTestQuestion(any(), any(), any());
    }

    private CurriculumLesson lesson(UUID testId) {
        return lessonWithCourse(testId, UUID.randomUUID());
    }

    private CurriculumLesson lessonWithCourse(UUID testId, UUID courseId) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setTitle("Sample lesson");
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
