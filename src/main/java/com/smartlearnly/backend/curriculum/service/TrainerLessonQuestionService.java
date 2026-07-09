package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.test.dto.TestModel;
import com.smartlearnly.backend.test.dto.TestQuestionModel;
import com.smartlearnly.backend.test.entity.TestType;
import com.smartlearnly.backend.test.service.TestQuestionService;
import com.smartlearnly.backend.test.service.TestService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trainer-scoped facade around {@link TestQuestionService} and {@link TestService}
 * that verifies ownership of the class draft before delegating to the shared
 * services. Each mutation ensures the lesson belongs to the trainer's class DRAFT
 * curriculum; reads accept DRAFT or PUBLISHED class curricula.
 */
@Service
@RequiredArgsConstructor
public class TrainerLessonQuestionService {

    private final TrainerClassCurriculumService trainerClassCurriculumService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final TestService testService;
    private final TestQuestionService testQuestionService;

    @Transactional(readOnly = true)
    public List<TestQuestionModel.Response> listQuestions(UUID classId, UUID lessonId) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId);
        UUID testId = lesson.getTestId();
        if (testId == null) {
            return List.of();
        }
        return testQuestionService.getQuestionsByTest(testId);
    }

    @Transactional
    public TestQuestionModel.Response attachQuestion(
            UUID classId,
            UUID lessonId,
            TestQuestionModel.AddRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question id is required");
        }
        UUID testId = ensureLessonTest(lesson);
        TestQuestionModel.AddRequest normalized = new TestQuestionModel.AddRequest();
        normalized.setTestId(testId);
        normalized.setQuestionId(request.getQuestionId());
        normalized.setOrderIndex(request.getOrderIndex());
        normalized.setMarks(request.getMarks() == null ? BigDecimal.ONE : request.getMarks());
        return testQuestionService.addQuestionToTest(normalized);
    }

    @Transactional
    public TestQuestionModel.Response updateQuestion(
            UUID classId,
            UUID lessonId,
            UUID questionId,
            TestQuestionModel.UpdateRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        UUID testId = requireLessonTest(lesson);
        return testQuestionService.updateTestQuestion(testId, questionId, request);
    }

    @Transactional
    public void detachQuestion(UUID classId, UUID lessonId, UUID questionId) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        UUID testId = requireLessonTest(lesson);
        testQuestionService.removeQuestionFromTest(testId, questionId);
    }

    @Transactional
    public List<TestQuestionModel.Response> reorderQuestions(
            UUID classId,
            UUID lessonId,
            ReorderRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        UUID testId = requireLessonTest(lesson);
        List<UUID> ids = request == null ? List.of() : request.ids();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Reorder list must not be empty");
        }
        Set<UUID> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Reorder list contains duplicate ids");
        }

        List<TestQuestionModel.Response> existing = testQuestionService.getQuestionsByTest(testId);
        Set<UUID> existingIds = new HashSet<>();
        for (TestQuestionModel.Response response : existing) {
            existingIds.add(response.getQuestionId());
        }
        if (!existingIds.equals(uniqueIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Reorder list must include every attached question exactly once"
            );
        }

        for (int index = 0; index < ids.size(); index++) {
            UUID questionId = ids.get(index);
            TestQuestionModel.Response current = findResponse(existing, questionId);
            TestQuestionModel.UpdateRequest update = new TestQuestionModel.UpdateRequest();
            update.setOrderIndex(index);
            update.setMarks(current == null || current.getMarks() == null ? BigDecimal.ONE : current.getMarks());
            testQuestionService.updateTestQuestion(testId, questionId, update);
        }
        return testQuestionService.getQuestionsByTest(testId);
    }

    private UUID ensureLessonTest(CurriculumLesson lesson) {
        UUID testId = lesson.getTestId();
        if (testId != null) {
            return testId;
        }
        TestModel.CreateRequest createRequest = new TestModel.CreateRequest();
        createRequest.setCourseId(lesson.getSection() == null || lesson.getSection().getCurriculumVersion() == null
                ? null
                : lesson.getSection().getCurriculumVersion().getCourseId());
        createRequest.setTitle(defaultTestTitle(lesson));
        createRequest.setDescription(null);
        createRequest.setTestType(TestType.practice);
        createRequest.setShuffleQuestions(false);
        createRequest.setShuffleAnswers(false);
        createRequest.setShowAnswersAfter(true);
        createRequest.setIsFlashtest(false);
        TestModel.Response created = testService.createTest(createRequest);
        UUID createdId = created.getId();
        lesson.setTestId(createdId);
        curriculumLessonRepository.save(lesson);
        return createdId;
    }

    private UUID requireLessonTest(CurriculumLesson lesson) {
        UUID testId = lesson.getTestId();
        if (testId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson does not have an attached test");
        }
        return testId;
    }

    private String defaultTestTitle(CurriculumLesson lesson) {
        String base = lesson == null ? null : lesson.getTitle();
        if (base == null || base.isBlank()) {
            return "Quiz";
        }
        String trimmed = base.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) : trimmed;
    }

    private TestQuestionModel.Response findResponse(List<TestQuestionModel.Response> list, UUID questionId) {
        for (TestQuestionModel.Response entry : list) {
            if (questionId.equals(entry.getQuestionId())) {
                return entry;
            }
        }
        return null;
    }
}
