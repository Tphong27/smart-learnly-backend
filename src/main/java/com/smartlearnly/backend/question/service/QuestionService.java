package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.BloomLevel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private static final int MIN_ANSWERS = 2;
    private static final int MAX_MCQ_ANSWERS = 6;

    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionAnswerMediaAttachmentRepository answerMediaRepository;
    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;
    private final QuestionBankService questionBankService;
    private final CourseSectionRepository courseSectionRepository;
    private final CurrentUserService currentUserService;
    private final QuestionMediaImportService questionMediaImportService;

    @Transactional(readOnly = true)
    public PageResponse<QuestionModel.Response> list(UUID bankId, UUID courseId, UUID moduleId, String search, String type, String status, Short difficulty, int page, int size) {
        String normalizedSearch = normalizeNullable(search);
        String normalizedType = type == null || type.isBlank()
                ? null
                : parseSupportedQuestionType(type).name().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : parseQuestionStatus(status, null).name().toLowerCase(Locale.ROOT);
        Page<Question> questionPage = questionRepository.searchForAdmin(
                bankId,
                courseId,
                moduleId,
                normalizedSearch,
                normalizedType,
                normalizedStatus,
                difficulty,
                PageRequest.of(page, size)
        );
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
        questionBankService.findActiveBankEntity(question.getQuestionBankId());
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
        questionBankService.findActiveBankEntity(question.getQuestionBankId());
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
        List<Question> savedQuestions = importReviewedRows(request.bankId(), request.rows(), false, null, normalizeImportMediaSource(request.importSource()));
        List<UUID> createdIds = savedQuestions.stream().map(Question::getId).toList();
        return new QuestionImportDtos.ImportBatchResponse(request.rows().size(), createdIds.size(), createdIds, List.of());
    }

    @Transactional
    public List<Question> importReviewedRows(UUID bankId, List<QuestionImportDtos.ImportRow> rows, boolean aiGenerated, String importSource) {
        return importReviewedRows(bankId, rows, aiGenerated, importSource, importSource == null ? null : importSource);
    }

    private List<Question> importReviewedRows(UUID bankId, List<QuestionImportDtos.ImportRow> rows, boolean aiGenerated, String importSource, String mediaImportSource) {
        QuestionBank bank = questionBankService.findActiveBankEntity(bankId);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return importReviewedRows(bank, rows, actor, aiGenerated, importSource, mediaImportSource);
    }

    private List<Question> importReviewedRows(
            QuestionBank bank, List<QuestionImportDtos.ImportRow> rows, UserAccount actor,
            boolean aiGenerated, String importSource, String mediaImportSource
    ) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one question row is required");
        }

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

        List<Question> savedQuestions = new ArrayList<>();
        for (QuestionImportDtos.ImportRow row : validatedRows) {
            if (duplicateRowNumbers.contains(row.rowNumber())) {
                continue;
            }
            Question savedQuestion = persistImportedQuestion(bank, row, actor, aiGenerated, importSource);
            questionMediaImportService.attachImportedMedia(savedQuestion, row.imageFiles(), row.audioFiles(), mediaImportSource);
            savedQuestions.add(savedQuestion);
        }
        return savedQuestions;
    }

    private Question persistImportedQuestion(QuestionBank bank, QuestionImportDtos.ImportRow row, UserAccount actor, boolean aiGenerated, String importSource) {
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
        question.setIsAiGenerated(aiGenerated);
        question.setImportSource(normalizeNullable(importSource));
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

        rowErrors.addAll(questionMediaImportService.validateMediaReferences(row.imageFiles(), row.audioFiles()));

        return rowErrors;
    }

    private String normalizeImportMediaSource(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) return "excel_import";
        String apiValue = normalized.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        return switch (apiValue) {
            case "excel_import", "json_import", "image_import" -> apiValue;
            default -> "excel_import";
        };
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
        List<QuestionAnswer> answerEntities = answerRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId());
        Map<UUID, List<QuestionAnswerMediaAttachment>> mediaByAnswer = answerEntities.isEmpty()
                ? Collections.emptyMap()
                : answerMediaRepository.findByAnswerIdIn(answerEntities.stream().map(QuestionAnswer::getId).toList()).stream()
                        .collect(Collectors.groupingBy(QuestionAnswerMediaAttachment::getAnswerId));
        List<QuestionModel.AnswerResponse> answers = answerEntities.stream().map(answer -> {
            List<QuestionAnswerMediaResponse> answerMedia = (mediaByAnswer.getOrDefault(answer.getId(), List.of())).stream()
                    .map(this::toAnswerMediaResponse)
                    .toList();
            return new QuestionModel.AnswerResponse(
                    answer.getId(),
                    answer.getId(),
                    answer.getAnswerText(),
                    Boolean.TRUE.equals(answer.getIsCorrect()),
                    Boolean.TRUE.equals(answer.getIsCorrect()),
                    answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                    answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                    answerMedia
            );
        }).toList();
        List<QuestionMediaAttachment> imageAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.IMAGE);
        List<QuestionMediaAttachment> audioAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.AUDIO);
        List<QuestionMediaAttachment> videoAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.VIDEO);
        List<QuestionMediaAttachmentResponse> mediaAttachments = new ArrayList<>();
        mediaAttachments.addAll(imageAttachments.stream().map(this::toMediaResponse).toList());
        mediaAttachments.addAll(audioAttachments.stream().map(this::toMediaResponse).toList());
        mediaAttachments.addAll(videoAttachments.stream().map(this::toMediaResponse).toList());
        String imageUrl = imageAttachments.isEmpty() ? null : imageAttachments.get(0).getMediaUrl();
        String audioUrl = audioAttachments.isEmpty() ? null : audioAttachments.get(0).getMediaUrl();
        return new QuestionModel.Response(question.getId(), question.getId(), question.getQuestionBankId(), question.getQuestionBankId(), question.getCourseId(), question.getModuleId(), question.getQuestionText(), toApiValue(question.getQuestionType()), toApiValue(question.getBloomLevel()), question.getDifficulty(), question.getExplanation(), imageUrl, audioUrl, mediaAttachments, Boolean.TRUE.equals(question.getIsAiGenerated()), question.getImportSource(), toApiValue(question.getStatus()), answers.size(), answers, question.getCreatedBy(), question.getReviewedBy(), question.getReviewedAt(), question.getCreatedAt(), question.getUpdatedAt());
    }

    private QuestionMediaAttachmentResponse toMediaResponse(QuestionMediaAttachment attachment) {
        return new QuestionMediaAttachmentResponse(
                attachment.getId(),
                attachment.getId(),
                attachment.getQuestionId(),
                toApiValue(attachment.getMediaType()),
                attachment.getMediaUrl(),
                attachment.getObjectKey(),
                attachment.getBucket(),
                attachment.getContentType(),
                attachment.getFileSize() == null ? 0 : attachment.getFileSize(),
                attachment.getOriginalFileName(),
                attachment.getDisplayOrder() == null ? 0 : attachment.getDisplayOrder(),
                attachment.getImportSource(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
    }

    private QuestionAnswerMediaResponse toAnswerMediaResponse(QuestionAnswerMediaAttachment attachment) {
        return new QuestionAnswerMediaResponse(
                attachment.getId(),
                attachment.getAnswerId(),
                toApiValue(attachment.getMediaType()),
                attachment.getMediaUrl(),
                attachment.getObjectKey(),
                attachment.getBucket(),
                attachment.getContentType(),
                attachment.getFileSize() == null ? 0 : attachment.getFileSize(),
                attachment.getOriginalFileName(),
                attachment.getDisplayOrder() == null ? 0 : attachment.getDisplayOrder(),
                attachment.getImportSource(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
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
