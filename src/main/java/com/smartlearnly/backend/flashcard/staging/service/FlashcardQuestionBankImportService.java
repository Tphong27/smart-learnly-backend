package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlashcardQuestionBankImportService {
    private static final Set<String> ACTIVE_IMPORTED_STATUSES = Set.of("draft", "approved");

    private final FlashcardSetRepository flashcardSetRepository;
    private final QuestionRepository questionRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final AdminFlashcardStagingService adminFlashcardStagingService;

    @Transactional
    public StagingBatchResponse importQuestionBank(UUID setId, ImportQuestionBankRequest request) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        UUID courseId = requireFlashcardCourseId(flashcardSet);

        List<UUID> questionIds = request.questionIds();
        Set<UUID> uniqueQuestionIds = new HashSet<>(questionIds);
        if (uniqueQuestionIds.size() != questionIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question import list contains duplicate ids");
        }

        List<Question> questions = questionRepository.findAllById(questionIds);
        if (questions.size() != uniqueQuestionIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "All submitted questions must exist");
        }

        Set<UUID> alreadyImportedIds = new HashSet<>(stagingCardRepository.findImportedSourceQuestionIds(
                setId,
                uniqueQuestionIds,
                ACTIVE_IMPORTED_STATUSES
        ));
        if (!alreadyImportedIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "One or more questions were already imported into this flashcard set"
            );
        }

        questions.forEach(question -> validateQuestion(question, courseId));
        return adminFlashcardStagingService.importQuestionBank(setId, request);
    }

    private UUID requireFlashcardCourseId(FlashcardSet flashcardSet) {
        Lesson lesson = flashcardSet.getLesson();
        if (lesson == null || lesson.getType() != LessonType.FLASHCARD || lesson.getCourse() == null
                || lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        return lesson.getCourse().getId();
    }

    private void validateQuestion(Question question, UUID courseId) {
        if (question.getStatus() != QuestionStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only approved questions can be imported");
        }
        if (!courseId.equals(question.getCourseId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question does not belong to this flashcard course");
        }
    }
}
