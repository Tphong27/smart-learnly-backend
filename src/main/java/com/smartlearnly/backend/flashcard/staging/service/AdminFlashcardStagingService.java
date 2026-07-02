package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionAnswerResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFlashcardStagingService {
    private static final String SOURCE_TYPE_QUESTION_BANK = "QUESTION_BANK";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final FlashcardStagingBatchRepository stagingBatchRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<SourceQuestionResponse> listSourceQuestions(
            UUID setId,
            UUID questionBankId,
            String keyword,
            Short difficulty,
            String status
    ) {
        SetContext context = resolveSetContext(setId);
        Specification<Question> specification = sourceQuestionSpecification(
                context.course().getId(),
                questionBankId,
                keyword,
                difficulty,
                parseQuestionStatus(status)
        );
        List<Question> questions = questionRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(question -> context.course().getId().equals(question.getCourseId()))
                .toList();
        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        Map<UUID, String> bankNames = bankNames(questions.stream().map(Question::getQuestionBankId).collect(Collectors.toSet()));

        return questions.stream()
                .map(question -> toSourceQuestionResponse(
                        question,
                        bankNames.get(question.getQuestionBankId()),
                        answersByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public StagingBatchResponse importQuestionBank(UUID setId, ImportQuestionBankRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null || request.questionIds() == null || request.questionIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one question id is required");
        }
        assertNoDuplicates(request.questionIds(), "Question import contains duplicate ids");
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<Question> questions = loadQuestionsInRequestOrder(request.questionIds());
        if (questions.size() != request.questionIds().size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more questions were not found");
        }
        for (Question question : questions) {
            if (!context.course().getId().equals(question.getCourseId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question must belong to the same course as the flashcard set");
            }
        }

        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        Map<UUID, String> bankNames = bankNames(questions.stream().map(Question::getQuestionBankId).collect(Collectors.toSet()));
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setFlashcardSet(context.flashcardSet());
        batch.setLesson(context.lesson());
        batch.setCourse(context.course());
        batch.setCreatedBy(actor);
        batch.setSourceType(SOURCE_TYPE_QUESTION_BANK);
        batch.setStatus(STATUS_DRAFT);
        batch.setSourceName(sourceName(bankNames));
        FlashcardStagingBatch savedBatch = stagingBatchRepository.save(batch);

        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (int index = 0; index < questions.size(); index += 1) {
            Question question = questions.get(index);
            List<QuestionAnswer> answers = answersByQuestionId.getOrDefault(question.getId(), List.of());
            FlashcardStagingCard card = new FlashcardStagingCard();
            card.setBatch(savedBatch);
            card.setSourceQuestionId(question.getId());
            card.setFrontText(normalizeRequired(question.getQuestionText(), "Question text is required"));
            card.setBackText(buildBackText(question, answers));
            card.setExplanation(normalizeNullable(question.getExplanation()));
            card.setSourceExcerpt(normalizeNullable(question.getQuestionText()));
            card.setStatus(STATUS_DRAFT);
            card.setSortOrder(index);
            validateCard(card);
            cards.add(card);
        }

        List<FlashcardStagingCard> savedCards = stagingCardRepository.saveAll(cards);
        return toBatchResponse(savedBatch, savedCards);
    }

    @Transactional(readOnly = true)
    public List<StagingBatchResponse> listStaging(UUID setId) {
        resolveSetContext(setId);
        List<FlashcardStagingBatch> batches = stagingBatchRepository.findByFlashcardSetIdAndStatusOrderByCreatedAtDesc(setId, STATUS_DRAFT);
        if (batches.isEmpty()) {
            return List.of();
        }
        List<UUID> batchIds = batches.stream().map(FlashcardStagingBatch::getId).toList();
        Map<UUID, List<FlashcardStagingCard>> cardsByBatchId = stagingCardRepository
                .findByBatchIdInOrderBySortOrderAscCreatedAtAsc(batchIds)
                .stream()
                .collect(Collectors.groupingBy(card -> card.getBatch().getId(), LinkedHashMap::new, Collectors.toList()));

        return batches.stream()
                .map(batch -> toBatchResponse(batch, cardsByBatchId.getOrDefault(batch.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StagingCardResponse updateCard(UUID stagingCardId, UpdateStagingCardRequest request) {
        FlashcardStagingCard card = findStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be edited");
        applyUpdate(card, request);
        validateCard(card);
        return toCardResponse(stagingCardRepository.save(card));
    }

    @Transactional
    public void rejectCard(UUID stagingCardId) {
        FlashcardStagingCard card = findStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be rejected");
        card.setStatus(STATUS_REJECTED);
        stagingCardRepository.save(card);
    }

    @Transactional
    public ApproveStagingCardsResponse approve(UUID setId, ApproveStagingCardsRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null || request.stagingCardIds() == null || request.stagingCardIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one staging card id is required");
        }
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<FlashcardStagingCard> cards = resolveCardsForApproval(setId, request.stagingCardIds());

        int nextOrderIndex = flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1;
        List<FlashcardCard> flashcards = new ArrayList<>();
        for (FlashcardStagingCard stagingCard : cards) {
            FlashcardCard flashcard = new FlashcardCard();
            flashcard.setFlashcardSet(context.flashcardSet());
            flashcard.setFrontText(stagingCard.getFrontText());
            flashcard.setFrontImageUrl(stagingCard.getFrontImageUrl());
            flashcard.setBackText(stagingCard.getBackText());
            flashcard.setBackImageUrl(stagingCard.getBackImageUrl());
            flashcard.setHint(stagingCard.getHint());
            flashcard.setExplanation(stagingCard.getExplanation());
            flashcard.setOrderIndex(nextOrderIndex);
            nextOrderIndex += 1;
            flashcards.add(flashcard);
            stagingCard.setStatus(STATUS_APPROVED);
        }

        List<FlashcardCard> savedFlashcards = flashcardCardRepository.saveAll(flashcards);
        stagingCardRepository.saveAll(cards);
        markFullyApprovedBatches(cards, actor);

        return new ApproveStagingCardsResponse(
                savedFlashcards.size(),
                savedFlashcards.stream().map(FlashcardCard::getId).toList()
        );
    }

    private List<FlashcardStagingCard> resolveCardsForApproval(UUID setId, List<UUID> requestedIds) {
        assertNoDuplicates(requestedIds, "Approval request contains duplicate staging card ids");
        Map<UUID, FlashcardStagingCard> cardsById = stagingCardRepository.findByIdIn(requestedIds).stream()
                .collect(Collectors.toMap(FlashcardStagingCard::getId, Function.identity()));
        if (cardsById.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more staging cards were not found");
        }

        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (UUID cardId : requestedIds) {
            FlashcardStagingCard card = cardsById.get(cardId);
            if (!setId.equals(card.getBatch().getFlashcardSet().getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Staging card must belong to the selected flashcard set");
            }
            requireDraftCard(card, "Only draft staging cards can be approved");
            cards.add(card);
        }
        return cards;
    }

    private void markFullyApprovedBatches(List<FlashcardStagingCard> cards, UserAccount actor) {
        Instant now = Instant.now();
        Map<UUID, FlashcardStagingBatch> batchesById = new LinkedHashMap<>();
        for (FlashcardStagingCard card : cards) {
            batchesById.put(card.getBatch().getId(), card.getBatch());
        }
        List<FlashcardStagingBatch> approvedBatches = new ArrayList<>();
        for (FlashcardStagingBatch batch : batchesById.values()) {
            if (stagingCardRepository.countByBatchIdAndStatus(batch.getId(), STATUS_DRAFT) == 0) {
                batch.setStatus(STATUS_APPROVED);
                batch.setApprovedAt(now);
                batch.setApprovedBy(actor);
                approvedBatches.add(batch);
            }
        }
        if (!approvedBatches.isEmpty()) {
            stagingBatchRepository.saveAll(approvedBatches);
        }
    }

    private Specification<Question> sourceQuestionSpecification(
            UUID courseId,
            UUID questionBankId,
            String keyword,
            Short difficulty,
            QuestionStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("courseId"), courseId));
            if (questionBankId != null) {
                predicates.add(criteriaBuilder.equal(root.get("questionBankId"), questionBankId));
            }
            String normalizedKeyword = normalizeNullable(keyword);
            if (normalizedKeyword != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("questionText")),
                        "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%"
                ));
            }
            if (difficulty != null) {
                predicates.add(criteriaBuilder.equal(root.get("difficulty"), difficulty));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SetContext resolveSetContext(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Lesson lesson = flashcardSet.getLesson();
        if (lesson == null || lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
        }
        return new SetContext(flashcardSet, lesson, lesson.getCourse());
    }

    private FlashcardStagingCard findStagingCard(UUID stagingCardId) {
        return stagingCardRepository.findById(stagingCardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard staging card was not found"));
    }

    private List<Question> loadQuestionsInRequestOrder(List<UUID> questionIds) {
        Map<UUID, Question> questionsById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<Question> questions = new ArrayList<>();
        for (UUID questionId : questionIds) {
            Question question = questionsById.get(questionId);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    private Map<UUID, List<QuestionAnswer>> answersByQuestionId(List<Question> questions) {
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionAnswer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<UUID, String> bankNames(Set<UUID> bankIds) {
        if (bankIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        questionBankRepository.findAllById(bankIds).forEach(bank -> names.put(bank.getId(), bank.getName()));
        return names;
    }

    private String sourceName(Map<UUID, String> bankNames) {
        List<String> names = bankNames.values().stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() > 1) {
            return "Question Bank Import (" + names.size() + " banks)";
        }
        return "Question Bank Import";
    }

    private String buildBackText(Question question, List<QuestionAnswer> answers) {
        List<String> correctAnswers = answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .toList();
        List<String> fallbackAnswers = answers.stream()
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .toList();

        StringBuilder builder = new StringBuilder();
        if (!correctAnswers.isEmpty()) {
            builder.append("Correct answer(s):\n").append(String.join("\n", correctAnswers));
        } else if (!fallbackAnswers.isEmpty()) {
            builder.append("Available answer(s):\n").append(String.join("\n", fallbackAnswers));
        }

        String explanation = normalizeNullable(question.getExplanation());
        if (explanation != null) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Explanation:\n").append(explanation);
        }

        if (builder.isEmpty()) {
            builder.append(normalizeRequired(question.getQuestionText(), "Question text is required"));
        }
        return builder.toString();
    }

    private void applyUpdate(FlashcardStagingCard card, UpdateStagingCardRequest request) {
        if (request.frontText() != null) {
            card.setFrontText(normalizeNullable(request.frontText()));
        }
        if (request.backText() != null) {
            card.setBackText(normalizeNullable(request.backText()));
        }
        if (request.frontImageUrl() != null) {
            card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        }
        if (request.backImageUrl() != null) {
            card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        }
        if (request.hint() != null) {
            card.setHint(normalizeNullable(request.hint()));
        }
        if (request.explanation() != null) {
            card.setExplanation(normalizeNullable(request.explanation()));
        }
        if (request.sortOrder() != null) {
            card.setSortOrder(request.sortOrder());
        }
    }

    private void validateCard(FlashcardStagingCard card) {
        boolean hasFront = hasText(card.getFrontText()) || hasText(card.getFrontImageUrl());
        boolean hasBack = hasText(card.getBackText()) || hasText(card.getBackImageUrl());
        if (!hasFront) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging front side requires text or image");
        }
        if (!hasBack) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging back side requires text or image");
        }
    }

    private void requireDraftCard(FlashcardStagingCard card, String message) {
        if (!STATUS_DRAFT.equals(card.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private void assertNoDuplicates(List<UUID> ids, String message) {
        Set<UUID> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private QuestionStatus parseQuestionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return QuestionStatus.valueOf(normalized);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question status is invalid");
        }
    }

    private SourceQuestionResponse toSourceQuestionResponse(Question question, String bankName, List<QuestionAnswer> answers) {
        List<SourceQuestionAnswerResponse> answerResponses = answers.stream()
                .sorted(Comparator.comparing(answer -> answer.getOrderIndex() == null ? 0 : answer.getOrderIndex()))
                .map(this::toSourceAnswerResponse)
                .toList();
        List<String> correctAnswers = answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(QuestionAnswer::getAnswerText)
                .toList();
        return new SourceQuestionResponse(
                question.getId(),
                question.getId(),
                question.getQuestionBankId(),
                bankName,
                question.getCourseId(),
                question.getModuleId(),
                question.getQuestionText(),
                toApiValue(question.getQuestionType()),
                question.getDifficulty(),
                toApiValue(question.getStatus()),
                question.getExplanation(),
                answerResponses,
                correctAnswers
        );
    }

    private SourceQuestionAnswerResponse toSourceAnswerResponse(QuestionAnswer answer) {
        int orderIndex = answer.getOrderIndex() == null ? 0 : answer.getOrderIndex();
        return new SourceQuestionAnswerResponse(
                answer.getId(),
                answer.getId(),
                answer.getAnswerText(),
                Boolean.TRUE.equals(answer.getIsCorrect()),
                Boolean.TRUE.equals(answer.getIsCorrect()),
                orderIndex,
                orderIndex
        );
    }

    private StagingBatchResponse toBatchResponse(FlashcardStagingBatch batch, List<FlashcardStagingCard> cards) {
        return new StagingBatchResponse(
                batch.getId(),
                batch.getFlashcardSet().getId(),
                batch.getLesson().getId(),
                batch.getCourse().getId(),
                batch.getSourceType(),
                batch.getStatus(),
                batch.getSourceName(),
                cards.stream()
                        .sorted(Comparator.comparing(card -> card.getSortOrder() == null ? 0 : card.getSortOrder()))
                        .map(this::toCardResponse)
                        .toList(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getApprovedAt(),
                batch.getApprovedBy() == null ? null : batch.getApprovedBy().getId()
        );
    }

    private StagingCardResponse toCardResponse(FlashcardStagingCard card) {
        return new StagingCardResponse(
                card.getId(),
                card.getBatch().getId(),
                card.getSourceQuestionId(),
                card.getFrontText(),
                card.getBackText(),
                card.getFrontImageUrl(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getSourceExcerpt(),
                card.getStatus(),
                card.getSortOrder(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SetContext(FlashcardSet flashcardSet, Lesson lesson, Course course) {
    }
}
