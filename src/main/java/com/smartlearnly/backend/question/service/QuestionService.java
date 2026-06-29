package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.BloomLevel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private static final int MIN_ANSWERS = 2;
    private static final int MAX_MCQ_ANSWERS = 6;

    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionBankService questionBankService;
    private final CourseSectionRepository courseSectionRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResponse<QuestionModel.Response> list(UUID bankId, UUID courseId, UUID moduleId, String search, String type, String status, Short difficulty, int page, int size) {
        Specification<Question> specification = buildSpecification(bankId, courseId, moduleId, search, type, status, difficulty);
        Page<Question> questionPage = questionRepository.findAll(specification, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new PageResponse<>(questionPage.getContent().stream().map(this::toResponse).toList(), questionPage.getNumber(), questionPage.getSize(), questionPage.getTotalElements(), questionPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public QuestionModel.Response get(UUID questionId) {
        return toResponse(findQuestion(questionId));
    }

    @Transactional
    public QuestionModel.Response create(QuestionModel.CreateRequest request) {
        QuestionBank bank = resolveBank(request.resolvedBankId(), request.courseId());
        QuestionType questionType = parseSupportedQuestionType(request.questionType());
        validateAnswers(questionType, request.answers());
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Question question = new Question();
        question.setQuestionBankId(bank.getId());
        question.setCourseId(bank.getCourseId());
        question.setModuleId(validateModuleId(bank.getCourseId(), request.moduleId()));
        question.setQuestionText(normalizeRequired(request.questionText(), "Question text is required"));
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(request.bloomLevel()));
        question.setDifficulty(request.difficulty());
        question.setExplanation(normalizeNullable(request.explanation()));
        question.setIsAiGenerated(false);
        question.setStatus(parseQuestionStatus(request.status(), QuestionStatus.DRAFT));
        question.setCreatedBy(actor.getId());
        Question saved = questionRepository.save(question);
        replaceAnswers(saved.getId(), request.answers());
        return toResponse(saved);
    }

    @Transactional
    public QuestionModel.Response update(UUID questionId, QuestionModel.UpdateRequest request) {
        Question question = findQuestion(questionId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot update an archived question");
        }
        QuestionBank bank = resolveBank(request.resolvedBankId() == null ? question.getQuestionBankId() : request.resolvedBankId(), request.courseId());
        QuestionType questionType = parseSupportedQuestionType(request.questionType());
        validateAnswers(questionType, request.answers());
        question.setQuestionBankId(bank.getId());
        question.setCourseId(bank.getCourseId());
        question.setModuleId(validateModuleId(bank.getCourseId(), request.moduleId()));
        question.setQuestionText(normalizeRequired(request.questionText(), "Question text is required"));
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(request.bloomLevel()));
        question.setDifficulty(request.difficulty());
        question.setExplanation(normalizeNullable(request.explanation()));
        question.setStatus(parseQuestionStatus(request.status(), question.getStatus()));
        Question saved = questionRepository.save(question);
        replaceAnswers(saved.getId(), request.answers());
        return toResponse(saved);
    }

    @Transactional
    public void archive(UUID questionId) {
        Question question = findQuestion(questionId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Question is already archived");
        }
        question.setStatus(QuestionStatus.ARCHIVED);
        questionRepository.save(question);
    }

    @Transactional
    public QuestionModel.Response approve(UUID questionId) {
        return review(questionId, QuestionStatus.APPROVED);
    }

    @Transactional
    public QuestionModel.Response reject(UUID questionId) {
        return review(questionId, QuestionStatus.REJECTED);
    }

    private QuestionModel.Response review(UUID questionId, QuestionStatus status) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Question question = findQuestion(questionId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot review an archived question");
        }
        question.setStatus(status);
        question.setReviewedBy(actor.getId());
        question.setReviewedAt(Instant.now());
        return toResponse(questionRepository.save(question));
    }

    private Specification<Question> buildSpecification(UUID bankId, UUID courseId, UUID moduleId, String search, String type, String status, Short difficulty) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (bankId != null) predicates.add(criteriaBuilder.equal(root.get("questionBankId"), bankId));
            if (courseId != null) {
                Subquery<UUID> courseBankIds = query.subquery(UUID.class);
                Root<QuestionBank> bankRoot = courseBankIds.from(QuestionBank.class);
                courseBankIds.select(bankRoot.get("id"))
                        .where(criteriaBuilder.equal(bankRoot.get("courseId"), courseId));
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("courseId"), courseId),
                        root.get("questionBankId").in(courseBankIds)
                ));
            }
            if (moduleId != null) predicates.add(criteriaBuilder.equal(root.get("moduleId"), moduleId));
            String normalizedSearch = normalizeNullable(search);
            if (normalizedSearch != null) predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("questionText")), "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%"));
            if (type != null && !type.isBlank()) predicates.add(criteriaBuilder.equal(root.get("questionType"), parseSupportedQuestionType(type)));
            if (status != null && !status.isBlank()) predicates.add(criteriaBuilder.equal(root.get("status"), parseQuestionStatus(status, null)));
            if (difficulty != null) predicates.add(criteriaBuilder.equal(root.get("difficulty"), difficulty));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private QuestionBank resolveBank(UUID bankId, UUID requestCourseId) {
        if (bankId == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question bank ID is required");
        QuestionBank bank = questionBankService.findActiveBankEntity(bankId);
        if (requestCourseId != null && !requestCourseId.equals(bank.getCourseId())) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question course must match the selected question bank");
        return bank;
    }

    private UUID validateModuleId(UUID courseId, UUID moduleId) {
        if (moduleId == null) return null;
        boolean exists = courseSectionRepository.findByIdAndCourseId(moduleId, courseId).isPresent();
        if (!exists) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question module must belong to the selected course");
        return moduleId;
    }

    @Transactional
    public QuestionImportDtos.ImportBatchResponse importBatch(QuestionImportDtos.ImportBatchRequest request) {
        QuestionBank bank = questionBankService.findActiveBankEntity(request.bankId());

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<QuestionImportDtos.ImportRow> rows = request.rows();

        List<QuestionImportDtos.ImportRowError> errors = new ArrayList<>();
        Set<Integer> duplicateRowNumbers = new HashSet<>();
        List<QuestionImportDtos.ImportRow> validatedRows = new ArrayList<>();
        for (QuestionImportDtos.ImportRow row : rows) {
            List<String> rowErrors = validateImportRow(bank, row);
            if (!rowErrors.isEmpty()) {
                errors.add(new QuestionImportDtos.ImportRowError(row.rowNumber(), rowErrors));
            } else {
                validatedRows.add(row);
            }
        }

        for (QuestionImportDtos.ImportRow row : validatedRows) {
            String normalizedText = normalizeRequired(row.questionText(), "Question text is required");
            if (questionRepository.existsByQuestionBankIdAndQuestionTextIgnoreCase(bank.getId(), normalizedText)) {
                duplicateRowNumbers.add(row.rowNumber());
                errors.add(new QuestionImportDtos.ImportRowError(row.rowNumber(), List.of("A question with the same text already exists in this bank")));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, buildImportErrorSummary(errors));
        }

        List<UUID> createdIds = new ArrayList<>();
        for (QuestionImportDtos.ImportRow row : validatedRows) {
            if (duplicateRowNumbers.contains(row.rowNumber())) {
                continue;
            }
            Question saved = persistImportedQuestion(bank, row, actor);
            createdIds.add(saved.getId());
        }

        return new QuestionImportDtos.ImportBatchResponse(rows.size(), createdIds.size(), createdIds, List.of());
    }

    private Question persistImportedQuestion(QuestionBank bank, QuestionImportDtos.ImportRow row, UserAccount actor) {
        QuestionType questionType = parseSupportedQuestionType(row.questionType());
        Question question = new Question();
        question.setQuestionBankId(bank.getId());
        question.setCourseId(bank.getCourseId());
        question.setModuleId(validateModuleId(bank.getCourseId(), row.moduleId()));
        question.setQuestionText(normalizeRequired(row.questionText(), "Question text is required"));
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(row.bloomLevel()));
        question.setDifficulty(row.difficulty());
        question.setExplanation(normalizeNullable(row.explanation()));
        question.setIsAiGenerated(false);
        question.setStatus(QuestionStatus.DRAFT);
        question.setCreatedBy(actor.getId());
        Question saved = questionRepository.save(question);

        List<QuestionModel.AnswerRequest> answers = buildAnswersForImport(row, questionType);
        replaceAnswers(saved.getId(), answers);
        return saved;
    }

    private List<QuestionModel.AnswerRequest> buildAnswersForImport(QuestionImportDtos.ImportRow row, QuestionType questionType) {
        List<String> options = row.options().stream()
                .map(option -> normalizeRequired(option, "Answer text is required"))
                .toList();
        int correctIndex = resolveCorrectAnswerIndex(questionType, options, row.correctAnswer());
        List<QuestionModel.AnswerRequest> answers = new ArrayList<>();
        for (int index = 0; index < options.size(); index += 1) {
            answers.add(new QuestionModel.AnswerRequest(
                    null,
                    null,
                    options.get(index),
                    index == correctIndex,
                    index == correctIndex,
                    index + 1,
                    index + 1
            ));
        }
        return answers;
    }

    private int resolveCorrectAnswerIndex(QuestionType questionType, List<String> options, String correctAnswer) {
        String normalized = correctAnswer == null ? "" : correctAnswer.trim();
        if (questionType == QuestionType.TRUE_FALSE) {
            boolean isTrue = "true".equalsIgnoreCase(normalized);
            boolean isFalse = "false".equalsIgnoreCase(normalized);
            if (!isTrue && !isFalse) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer for true/false must be True or False");
            }
            for (int index = 0; index < options.size(); index += 1) {
                String text = options.get(index).trim().toLowerCase(Locale.ROOT);
                if (isTrue && "true".equals(text)) return index;
                if (isFalse && "false".equals(text)) return index;
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false options must contain True and False answers");
        }
        if (normalized.length() != 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer must be a single letter A-F");
        }
        char letter = Character.toUpperCase(normalized.charAt(0));
        if (letter < 'A' || letter > 'F') {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer must be A, B, C, D, E, or F");
        }
        int index = letter - 'A';
        if (index >= options.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer refers to an option that was not provided");
        }
        return index;
    }

    private List<String> validateImportRow(QuestionBank bank, QuestionImportDtos.ImportRow row) {
        List<String> rowErrors = new ArrayList<>();
        if (row.questionText() == null || row.questionText().isBlank()) {
            rowErrors.add("Question text is required");
        } else if (row.questionText().length() > 10000) {
            rowErrors.add("Question text must not exceed 10000 characters");
        }

        QuestionType type = null;
        String rawType = row.questionType();
        if (rawType == null || rawType.isBlank()) {
            rowErrors.add("Question type is required");
        } else {
            String normalizedType = rawType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                type = QuestionType.valueOf(normalizedType);
            } catch (IllegalArgumentException exception) {
                rowErrors.add("Question type must be multiple_choice or true_false");
            }
            if (type != null && type != QuestionType.MULTIPLE_CHOICE && type != QuestionType.TRUE_FALSE) {
                rowErrors.add("Question type must be multiple_choice or true_false");
                type = null;
            }
        }

        List<String> options = row.options();
        if (options == null || options.size() < 2) {
            rowErrors.add("At least two answers are required");
        } else if (options.size() > 6) {
            rowErrors.add("Multiple choice questions support 2 to 6 answers");
        } else {
            for (int index = 0; index < options.size(); index += 1) {
                String option = options.get(index);
                if (option == null || option.isBlank()) {
                    rowErrors.add("Answer " + (char) ('A' + index) + " is required");
                } else if (option.length() > 4000) {
                    rowErrors.add("Answer " + (char) ('A' + index) + " must not exceed 4000 characters");
                }
            }
        }

        if (options != null && type == QuestionType.TRUE_FALSE && options.size() != 2) {
            rowErrors.add("True/false questions must have exactly two answers");
        }

        if (options != null && type == QuestionType.TRUE_FALSE) {
            boolean hasTrue = false;
            boolean hasFalse = false;
            for (String option : options) {
                if (option == null) continue;
                String text = option.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(text)) hasTrue = true;
                if ("false".equals(text)) hasFalse = true;
            }
            if (!hasTrue || !hasFalse) {
                rowErrors.add("True/false answers must be True and False");
            }
        }

        String correctAnswer = row.correctAnswer();
        if (correctAnswer == null || correctAnswer.isBlank()) {
            rowErrors.add("Correct answer is required");
        } else if (type == QuestionType.TRUE_FALSE) {
            String normalized = correctAnswer.trim();
            if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
                rowErrors.add("Correct answer for true/false must be True or False");
            }
        } else if (type == QuestionType.MULTIPLE_CHOICE) {
            String normalized = correctAnswer.trim();
            if (normalized.length() != 1) {
                rowErrors.add("Correct answer must be a single letter A-F");
            } else {
                char letter = Character.toUpperCase(normalized.charAt(0));
                if (letter < 'A' || letter > 'F') {
                    rowErrors.add("Correct answer must be A, B, C, D, E, or F");
                } else if (options != null && (letter - 'A') >= options.size()) {
                    rowErrors.add("Correct answer refers to an option that was not provided");
                }
            }
        }

        if (row.difficulty() != null && (row.difficulty() < 1 || row.difficulty() > 5)) {
            rowErrors.add("Difficulty must be between 1 and 5");
        }

        if (row.bloomLevel() != null && !row.bloomLevel().isBlank()) {
            String normalized = row.bloomLevel().trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                BloomLevel.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                rowErrors.add("Bloom level is invalid");
            }
        }

        if (row.moduleId() != null) {
            boolean moduleExists = courseSectionRepository.findByIdAndCourseId(row.moduleId(), bank.getCourseId()).isPresent();
            if (!moduleExists) {
                rowErrors.add("Question module must belong to the selected course");
            }
        }

        if (row.explanation() != null && row.explanation().length() > 10000) {
            rowErrors.add("Explanation must not exceed 10000 characters");
        }

        return rowErrors;
    }

    private String buildImportErrorSummary(List<QuestionImportDtos.ImportRowError> errors) {
        StringBuilder builder = new StringBuilder("Import validation failed:");
        int limit = Math.min(errors.size(), 5);
        for (int index = 0; index < limit; index += 1) {
            QuestionImportDtos.ImportRowError error = errors.get(index);
            builder.append(" Row ").append(error.rowNumber()).append(": ")
                    .append(String.join("; ", error.errors())).append('.');
        }
        if (errors.size() > limit) {
            builder.append(" And ").append(errors.size() - limit).append(" more rows with errors.");
        }
        return builder.toString();
    }

    private Question findQuestion(UUID questionId) {
        return questionRepository.findById(questionId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
    }

    private void replaceAnswers(UUID questionId, List<QuestionModel.AnswerRequest> answers) {
        answerRepository.deleteByQuestionId(questionId);
        for (int index = 0; index < answers.size(); index += 1) {
            QuestionModel.AnswerRequest request = answers.get(index);
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestionId(questionId);
            answer.setAnswerText(normalizeRequired(request.answerText(), "Answer text is required"));
            answer.setIsCorrect(request.correctValue());
            answer.setOrderIndex(request.resolvedOrder() == null ? index + 1 : request.resolvedOrder());
            answerRepository.save(answer);
        }
    }

    private void validateAnswers(QuestionType questionType, List<QuestionModel.AnswerRequest> answers) {
        if (answers == null || answers.size() < MIN_ANSWERS) throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least two answers are required");
        long correctCount = answers.stream().filter(QuestionModel.AnswerRequest::correctValue).count();
        if (correctCount != 1) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Exactly one correct answer is required");
        for (QuestionModel.AnswerRequest answer : answers) normalizeRequired(answer.answerText(), "Answer text is required");
        if (questionType == QuestionType.MULTIPLE_CHOICE && answers.size() > MAX_MCQ_ANSWERS) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Multiple choice questions support 2 to 6 answers");
        if (questionType == QuestionType.TRUE_FALSE) validateTrueFalseAnswers(answers);
    }

    private void validateTrueFalseAnswers(List<QuestionModel.AnswerRequest> answers) {
        if (answers.size() != 2) throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false questions must have exactly two answers");
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (QuestionModel.AnswerRequest answer : answers) {
            String text = normalizeRequired(answer.answerText(), "Answer text is required").toLowerCase(Locale.ROOT);
            hasTrue = hasTrue || "true".equals(text);
            hasFalse = hasFalse || "false".equals(text);
        }
        if (!hasTrue || !hasFalse) throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false answers must be True and False");
    }

    private QuestionModel.Response toResponse(Question question) {
        List<QuestionModel.AnswerResponse> answers = answerRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId()).stream().map(answer -> new QuestionModel.AnswerResponse(answer.getId(), answer.getId(), answer.getAnswerText(), Boolean.TRUE.equals(answer.getIsCorrect()), Boolean.TRUE.equals(answer.getIsCorrect()), answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(), answer.getOrderIndex() == null ? 0 : answer.getOrderIndex())).toList();
        return new QuestionModel.Response(question.getId(), question.getId(), question.getQuestionBankId(), question.getQuestionBankId(), question.getCourseId(), question.getModuleId(), question.getQuestionText(), toApiValue(question.getQuestionType()), toApiValue(question.getBloomLevel()), question.getDifficulty(), question.getExplanation(), Boolean.TRUE.equals(question.getIsAiGenerated()), toApiValue(question.getStatus()), answers.size(), answers, question.getCreatedBy(), question.getReviewedBy(), question.getReviewedAt(), question.getCreatedAt(), question.getUpdatedAt());
    }

    private QuestionType parseSupportedQuestionType(String value) {
        QuestionType type = parseEnum(value, QuestionType.class, "Question type must be multiple_choice or true_false");
        if (type != QuestionType.MULTIPLE_CHOICE && type != QuestionType.TRUE_FALSE) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question type must be multiple_choice or true_false");
        return type;
    }

    private QuestionStatus parseQuestionStatus(String value, QuestionStatus defaultStatus) {
        if (value == null || value.isBlank()) return defaultStatus;
        return parseEnum(value, QuestionStatus.class, "Question status is invalid");
    }

    private BloomLevel parseBloomLevel(String value) {
        if (value == null || value.isBlank()) return null;
        return parseEnum(value, BloomLevel.class, "Bloom level is invalid");
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, normalized);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
