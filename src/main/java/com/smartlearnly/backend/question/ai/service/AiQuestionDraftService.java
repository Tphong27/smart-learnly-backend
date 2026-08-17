package com.smartlearnly.backend.question.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraft;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraftRevision;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationEvidence;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSourceChunk;
import com.smartlearnly.backend.question.ai.generation.QuestionAiGenerationProperties;
import com.smartlearnly.backend.question.ai.generation.QuestionGenerationProvider;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationBatchRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRevisionRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationEvidenceRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceChunkRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceRepository;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AiQuestionDraftService {
    private static final String PROMPT_TEMPLATE_VERSION = "question-ai-generation-v1";
    private static final String IMPORT_SOURCE_AI_GENERATION = "ai_generation";
    private static final int MAX_INSTRUCTION_LENGTH = 2000;
    private static final int MAX_NEAR_DUPLICATE_CANDIDATES = 3;
    private static final int NEAR_DUPLICATE_PREFILTER_LIMIT = MAX_NEAR_DUPLICATE_CANDIDATES * 2;
    private static final double NEAR_DUPLICATE_THRESHOLD = 0.86D;
    private static final String SUPPORTED_QUESTION_TYPE_MESSAGE = "Question type must be single_choice, multiple_choice, or true_false";
    private static final Set<String> SUPPORTED_QUESTION_TYPES = Set.of("single_choice", "multiple_choice",
            "true_false");

    private final CourseAccessService courseAccessService;
    private final CurrentUserService currentUserService;
    private final CourseModuleRepository courseModuleRepository;
    private final AiQuestionGenerationBatchRepository batchRepository;
    private final AiQuestionGenerationSourceRepository sourceRepository;
    private final AiQuestionGenerationDraftRepository draftRepository;
    private final AiQuestionGenerationEvidenceRepository evidenceRepository;
    private final AiQuestionGenerationDraftRevisionRepository revisionRepository;
    private final AiQuestionGenerationSourceChunkRepository sourceChunkRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionGenerationProvider generationProvider;
    private final QuestionAiGenerationProperties properties;
    private final AiQuestionSourceService sourceService;
    private final ObjectMapper objectMapper;
    private NotificationService notificationService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.BatchResponse> listBatches(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);
        return batchRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(this::toBatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiQuestionDraftDtos.BatchResponse getBatch(UUID courseId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.DraftResponse> listDrafts(UUID courseId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        return draftRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                .map(this::toDraftResponse)
                .toList();
    }

    @Transactional
    public AiQuestionDraftDtos.BatchResponse createBatch(UUID courseId,
            AiQuestionDraftDtos.CreateBatchRequest request) {
        return createBatch(courseId, request, List.of());
    }

    @Transactional
    public AiQuestionDraftDtos.BatchResponse createBatch(UUID courseId, AiQuestionDraftDtos.CreateBatchRequest request,
            List<MultipartFile> files) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        courseAccessService.requireUpdatableCourse(courseId);
        String idempotencyKey = normalizeRequired(request.idempotencyKey(), "Idempotency key is required");
        var existing = batchRepository.findByRequestedByAndIdempotencyKey(actor.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return toBatchResponse(existing.get());
        }

        validateQuota(actor.getId());
        if (batchRepository.existsByRequestedByAndCourseIdAndStatus(actor.getId(), courseId,
                AiQuestionGenerationBatch.STATUS_PROCESSING)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG,
                    "Another AI generation batch is still processing for this course");
        }

        List<String> questionTypes = normalizeQuestionTypes(request.questionTypes());
        int requestedCount = normalizeRequestedCount(request.requestedCount());
        String language = normalizeLanguage(request.language());
        String instruction = normalizeInstruction(request.generationInstruction());
        validateModuleId(courseId, request.moduleId());

        AiQuestionGenerationBatch batch = new AiQuestionGenerationBatch();
        batch.setCourseId(courseId);
        batch.setRequestedBy(actor.getId());
        batch.setStatus(AiQuestionGenerationBatch.STATUS_REQUESTED);
        batch.setGenerationInstruction(instruction);
        batch.setInstructionSnapshot(instruction == null
                ? "Generate grounded draft questions from only the provided source content."
                : instruction);
        batch.setRequestedQuestionTypes(String.join(",", questionTypes));
        batch.setRequestedCount(requestedCount);
        batch.setLanguage(language);
        batch.setPromptTemplateVersion(PROMPT_TEMPLATE_VERSION);
        batch.setProvider(generationProvider.providerName());
        batch.setModel(generationProvider.modelName());
        batch.setIdempotencyKey(idempotencyKey);
        batch.setQuotaCharged(true);
        batch = batchRepository.save(batch);

        List<QuestionGenerationProvider.SourceInput> sourceInputs = sourceService.persistAndBuildSourceInputs(courseId,
                batch, request, files);

        batch.setStatus(AiQuestionGenerationBatch.STATUS_PROCESSING);
        batch = batchRepository.save(batch);
        generateAndPersistDrafts(batch, request.moduleId(), questionTypes, sourceInputs);
        return toBatchResponse(batchRepository.save(batch));
    }

    @Transactional
    public AiQuestionDraftDtos.BatchResponse retry(UUID courseId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        if (!AiQuestionGenerationBatch.STATUS_FAILED.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.AI_BATCH_NOT_RETRYABLE, "Only FAILED batches can be retried");
        }
        if (batch.getRetryCount() != null && batch.getRetryCount() >= 1) {
            throw new BusinessException(ErrorCode.AI_BATCH_NOT_RETRYABLE, "This batch has already used its retry");
        }
        batch.setRetryCount((batch.getRetryCount() == null ? 0 : batch.getRetryCount()) + 1);
        batch.setStatus(AiQuestionGenerationBatch.STATUS_PROCESSING);
        batch.setErrorCode(null);
        batch.setSafeErrorMessage(null);
        generateAndPersistDrafts(
                batch,
                null,
                parseQuestionTypesCsv(batch.getRequestedQuestionTypes()),
                sourceService.buildSourceInputsForBatch(batch.getId()));
        return toBatchResponse(batchRepository.save(batch));
    }

    @Transactional
    public AiQuestionDraftDtos.DraftResponse updateDraft(UUID courseId, UUID batchId, UUID draftId,
            AiQuestionDraftDtos.UpdateDraftRequest request) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        AiQuestionGenerationDraft draft = findDraftInBatch(batch, draftId);
        ensureVersion(draft, request.version());
        ensureDraftEditable(draft);
        validateModuleId(batch.getCourseId(), request.moduleId());

        String before = draftSnapshot(draft);
        List<AiQuestionDraftDtos.AnswerPayload> previousAnswers = parseAnswers(draft.getAnswersJson());
        String previousQuestionText = draft.getQuestionText();

        draft.setQuestionText(normalizeRequired(request.questionText(), "Question text is required"));
        draft.setExplanation(normalizeNullable(request.explanation()));
        draft.setModuleId(request.moduleId());
        draft.setAnswersJson(toJson(normalizeAnswers(request.answers())));

        boolean contentChanged = !normalizeForCompare(previousQuestionText)
                .equals(normalizeForCompare(draft.getQuestionText()))
                || correctAnswerChanged(previousAnswers, request.answers());
        if (contentChanged) {
            markEvidenceNeedsReview(draft);
        }
        applyDraftValidation(batch.getCourseId(), draft, evidenceRepository.findByDraftId(draft.getId()),
                evidenceRequired(batch.getId()));
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), currentUserService.requireAuthenticatedUser().getId(), before,
                draftSnapshot(saved), "edited");
        refreshBatchCounts(batch);
        return toDraftResponse(saved);
    }

    @Transactional
    public AiQuestionDraftDtos.DraftResponse rejectDraft(UUID courseId, UUID batchId, UUID draftId,
            AiQuestionDraftDtos.RejectDraftRequest request) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        AiQuestionGenerationDraft draft = findDraftInBatch(batch, draftId);
        ensureVersion(draft, request.version());
        ensureDraftEditable(draft);
        String before = draftSnapshot(draft);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        draft.setStatus(AiQuestionGenerationDraft.STATUS_REJECTED);
        draft.setReviewedBy(actor.getId());
        draft.setReviewedAt(Instant.now());
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), actor.getId(), before, draftSnapshot(saved), "rejected");
        refreshBatchCounts(batch);
        return toDraftResponse(saved);
    }

    @Transactional
    public AiQuestionDraftDtos.DraftResponse confirmEvidence(UUID courseId, UUID batchId, UUID draftId,
            AiQuestionDraftDtos.EvidenceConfirmationRequest request) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        AiQuestionGenerationDraft draft = findDraftInBatch(batch, draftId);
        ensureVersion(draft, request.version());
        ensureDraftEditable(draft);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String before = draftSnapshot(draft);
        List<AiQuestionGenerationEvidence> evidences = evidenceRepository.findByDraftId(draft.getId());
        for (AiQuestionGenerationEvidence evidence : evidences) {
            evidence.setEvidenceStatus(request.evidenceStillFits()
                    ? AiQuestionGenerationDraft.EVIDENCE_VALID
                    : AiQuestionGenerationDraft.EVIDENCE_INVALID);
            evidence.setReviewerConfirmedBy(actor.getId());
            evidence.setReviewerConfirmedAt(Instant.now());
            evidenceRepository.save(evidence);
        }
        draft.setEvidenceStatus(request.evidenceStillFits()
                ? AiQuestionGenerationDraft.EVIDENCE_VALID
                : AiQuestionGenerationDraft.EVIDENCE_INVALID);
        applyDraftValidation(batch.getCourseId(), draft, evidences, evidenceRequired(batch.getId()));
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), actor.getId(), before, draftSnapshot(saved), "evidence_confirmed");
        refreshBatchCounts(batch);
        return toDraftResponse(saved);
    }

    @Transactional
    public AiQuestionDraftDtos.AddSelectedResponse addSelected(UUID courseId, UUID batchId,
            AiQuestionDraftDtos.AddSelectedRequest request) {
        AiQuestionGenerationBatch batch = findBatch(courseId, batchId);
        courseAccessService.requireUpdatableCourse(courseId);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<AiQuestionDraftDtos.CreatedQuestion> created = new ArrayList<>();
        List<AiQuestionDraftDtos.SkippedItem> skipped = new ArrayList<>();

        for (AiQuestionDraftDtos.SelectedDraft selected : request.drafts()) {
            AiQuestionGenerationDraft draft = draftRepository.findById(selected.draftId()).orElse(null);
            if (draft == null || !batch.getId().equals(draft.getBatchId())) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(selected.draftId(), "AI_DRAFT_INVALID",
                        "Draft does not belong to this batch"));
                continue;
            }
            if (!selected.version().equals(draft.getVersion())) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(draft.getId(), "AI_DRAFT_VERSION_CONFLICT",
                        "Draft has been updated, please reload"));
                continue;
            }
            String skipReason = acceptBlockReason(courseId, batch, draft);
            if (skipReason != null) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(draft.getId(), skipReason, messageForSkip(skipReason)));
                continue;
            }
            Question question = persistQuestionFromDraft(courseId, draft, actor);
            String before = draftSnapshot(draft);
            draft.setStatus(AiQuestionGenerationDraft.STATUS_ACCEPTED);
            draft.setReviewedBy(actor.getId());
            draft.setReviewedAt(Instant.now());
            draft.setCreatedQuestionId(question.getId());
            draftRepository.save(draft);
            recordRevision(draft.getId(), actor.getId(), before, draftSnapshot(draft), "accepted");
            created.add(new AiQuestionDraftDtos.CreatedQuestion(draft.getId(), question.getId()));
        }
        refreshBatchCounts(batch);
        return new AiQuestionDraftDtos.AddSelectedResponse(created, skipped);
    }

    /** Sinh draft, lưu kết quả và phát notification khi batch hoàn tất. */
    private void generateAndPersistDrafts(
            AiQuestionGenerationBatch batch,
            UUID moduleId,
            List<String> questionTypes,
            List<QuestionGenerationProvider.SourceInput> sourceInputs) {
        try {
            QuestionGenerationProvider.GenerationResult result = generationProvider
                    .generate(new QuestionGenerationProvider.GenerationRequest(
                            batch.getId(),
                            batch.getRequestedCount(),
                            questionTypes,
                            batch.getLanguage(),
                            batch.getInstructionSnapshot(),
                            sourceInputs));
            List<QuestionGenerationProvider.GeneratedQuestion> generatedQuestions =
                    result.questions() == null ? List.of() : result.questions();
            int requestedLimit = Math.max(0, batch.getRequestedCount() == null ? 0 : batch.getRequestedCount());
            int generated = 0;
            boolean evidenceRequired = !sourceInputs.isEmpty();
            for (QuestionGenerationProvider.GeneratedQuestion generatedQuestion : generatedQuestions.stream()
                    .limit(requestedLimit)
                    .toList()) {
                persistGeneratedDraft(batch, moduleId, generatedQuestion, evidenceRequired);
                generated += 1;
            }
            batch.setGeneratedCount(generated);
            batch.setUsagePromptTokens(result.promptTokens());
            batch.setUsageCompletionTokens(result.completionTokens());
            batch.setUsageTotalTokens(result.totalTokens());
            batch.setStatus(
                    generated > 0 ? AiQuestionGenerationBatch.STATUS_READY : AiQuestionGenerationBatch.STATUS_FAILED);
            if (generated == 0) {
                batch.setErrorCode(ErrorCode.AI_PROVIDER_OUTPUT_INVALID.name());
                batch.setSafeErrorMessage("AI provider did not return usable draft questions");
            }
            batch.setCompletedAt(Instant.now());
            refreshBatchCounts(batch);
            emitAiBatchNotification(batch, moduleId);
        } catch (BusinessException exception) {
            batch.setStatus(AiQuestionGenerationBatch.STATUS_FAILED);
            batch.setErrorCode(exception.errorCode().name());
            batch.setSafeErrorMessage(exception.getMessage());
            batch.setCompletedAt(Instant.now());
            refreshBatchCounts(batch);
            emitAiBatchNotification(batch, moduleId);
        }
    }

    /** Phát notification AI với deep-link đúng module khi batch kết thúc. */
    private void emitAiBatchNotification(AiQuestionGenerationBatch batch, UUID moduleId) {
        if (notificationService == null || batch.getRequestedBy() == null) {
            return;
        }
        boolean ready = AiQuestionGenerationBatch.STATUS_READY.equals(batch.getStatus());
        boolean failed = AiQuestionGenerationBatch.STATUS_FAILED.equals(batch.getStatus());
        if (!ready && !failed) {
            return;
        }
        notificationService.emit(new NotificationCreateCommand(
                batch.getRequestedBy(),
                NotificationType.AI_SUGGESTION,
                ready ? "AI question drafts are ready" : "AI question generation failed",
                ready
                        ? "Review the generated draft questions before adding them to the course."
                        : (batch.getSafeErrorMessage() == null
                                ? "The AI provider did not return usable draft questions."
                                : batch.getSafeErrorMessage()),
                "AI_QUESTION_BATCH",
                batch.getId(),
                moduleId == null
                        ? "/admin/courses/" + batch.getCourseId() + "/questions/ai-drafts/" + batch.getId()
                        : "/admin/courses/" + batch.getCourseId() + "/modules/" + moduleId
                                + "/questions/ai-drafts/" + batch.getId(),
                null,
                "ai-question-batch:" + batch.getId() + ":" + batch.getStatus(),
                NotificationPayloads.of(
                        "courseId", batch.getCourseId(),
                        "moduleId", moduleId,
                        "status", batch.getStatus(),
                        "generatedCount", batch.getGeneratedCount() == null ? 0 : batch.getGeneratedCount())));
    }

    private void persistGeneratedDraft(
            AiQuestionGenerationBatch batch,
            UUID moduleId,
            QuestionGenerationProvider.GeneratedQuestion generatedQuestion,
            boolean evidenceRequired) {
        AiQuestionGenerationDraft draft = new AiQuestionGenerationDraft();
        draft.setBatchId(batch.getId());
        draft.setStatus(AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT);
        draft.setQuestionText(
                normalizeRequired(generatedQuestion.questionText(), "Generated question text is required"));
        draft.setQuestionType(normalizeQuestionType(generatedQuestion.questionType()));
        draft.setExplanation(normalizeNullable(generatedQuestion.explanation()));
        draft.setModuleId(moduleId);
        draft.setAnswersJson(toJson(normalizeAnswers(generatedQuestion.answers())));
        List<AiQuestionGenerationEvidence> evidences = new ArrayList<>();
        applyDraftValidation(batch.getCourseId(), draft, evidences, evidenceRequired);
        draft = draftRepository.save(draft);

        List<QuestionGenerationProvider.GeneratedEvidence> generatedEvidences = evidenceRequired
                && generatedQuestion.evidence() != null
                        ? generatedQuestion.evidence()
                        : List.of();
        for (QuestionGenerationProvider.GeneratedEvidence generatedEvidence : generatedEvidences) {
            AiQuestionGenerationEvidence evidence = new AiQuestionGenerationEvidence();
            evidence.setDraftId(draft.getId());
            evidence.setGenerationSourceId(generatedEvidence.generationSourceId());
            AiQuestionGenerationSourceChunk sourceChunk = sourceChunkRepository.findById(generatedEvidence.chunkId())
                    .orElse(null);
            evidence.setSourceChunkId(sourceChunk == null ? null : sourceChunk.getId());
            evidence.setChunkReference(
                    normalizeRequired(generatedEvidence.chunkReference(), "Evidence chunk reference is required"));
            evidence.setSourceExcerpt(normalizeRequired(generatedEvidence.excerpt(), "Evidence excerpt is required"));
            evidence.setStartMs(sourceChunk == null ? null : sourceChunk.getStartMs());
            evidence.setEndMs(sourceChunk == null ? null : sourceChunk.getEndMs());
            evidence.setSupportsCorrectAnswer(generatedEvidence.supportsCorrectAnswer());
            evidence.setEvidenceStatus(generatedEvidence.supportsCorrectAnswer()
                    ? AiQuestionGenerationDraft.EVIDENCE_VALID
                    : AiQuestionGenerationDraft.EVIDENCE_INVALID);
            evidences.add(evidenceRepository.save(evidence));
        }
        applyDraftValidation(batch.getCourseId(), draft, evidences, evidenceRequired);
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), batch.getRequestedBy(), null, draftSnapshot(saved), "generated");
    }

    private String acceptBlockReason(UUID courseId, AiQuestionGenerationBatch batch, AiQuestionGenerationDraft draft) {
        if (!AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT.equals(draft.getStatus())) {
            return "AI_DRAFT_INVALID";
        }
        if (AiQuestionGenerationDraft.VALIDATION_INVALID.equals(draft.getValidationStatus())) {
            return "AI_DRAFT_INVALID";
        }
        if (evidenceRequired(batch.getId())
                && !AiQuestionGenerationDraft.EVIDENCE_VALID.equals(draft.getEvidenceStatus())) {
            return "AI_EVIDENCE_REQUIRED";
        }
        if (questionRepository.existsActiveDuplicateInCourse(courseId, draft.getQuestionText(), null)) {
            return "AI_EXACT_DUPLICATE_ACTIVE";
        }
        return null;
    }

    private Question persistQuestionFromDraft(UUID courseId, AiQuestionGenerationDraft draft, UserAccount actor) {
        Question question = new Question();
        question.setCourseId(courseId);
        question.setModuleId(validateModuleId(courseId, draft.getModuleId()));
        question.setQuestionText(draft.getQuestionText());
        question.setQuestionType(QuestionType.valueOf(draft.getQuestionType().toUpperCase(Locale.ROOT)));
        question.setDifficulty(null);
        question.setExplanation(normalizeNullable(draft.getExplanation()));
        question.setIsAiGenerated(true);
        question.setImportSource(IMPORT_SOURCE_AI_GENERATION);
        question.setStatus(QuestionStatus.DRAFT);
        question.setCreatedBy(actor.getId());
        Question saved = questionRepository.save(question);
        List<AiQuestionDraftDtos.AnswerPayload> answers = parseAnswers(draft.getAnswersJson());
        for (int index = 0; index < answers.size(); index += 1) {
            AiQuestionDraftDtos.AnswerPayload answerPayload = answers.get(index);
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestionId(saved.getId());
            answer.setAnswerText(normalizeRequired(answerPayload.answerText(), "Answer text is required"));
            answer.setIsCorrect(answerPayload.correctValue());
            answer.setOrderIndex(answerPayload.orderIndex() == null ? index + 1 : answerPayload.orderIndex());
            answerRepository.save(answer);
        }
        return saved;
    }

    private void applyDraftValidation(
            UUID courseId,
            AiQuestionGenerationDraft draft,
            List<AiQuestionGenerationEvidence> evidences,
            boolean evidenceRequired) {
        List<String> warnings = new ArrayList<>();
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> duplicateCandidates = duplicateCandidates(courseId,
                draft.getQuestionText());
        boolean activeExactDuplicate = duplicateCandidates.stream()
                .anyMatch(candidate -> "exact".equals(candidate.matchType()) && !"archived".equals(candidate.status()));
        if (!duplicateCandidates.isEmpty() && !activeExactDuplicate) {
            warnings.add("Potential duplicate question found in this course");
        }

        boolean structurallyValid = isDraftStructurallyValid(draft, warnings);
        boolean hasSupportingEvidence = evidences.stream()
                .anyMatch(evidence -> Boolean.TRUE.equals(evidence.getSupportsCorrectAnswer())
                        && AiQuestionGenerationDraft.EVIDENCE_VALID.equals(evidence.getEvidenceStatus()));
        if (evidenceRequired && !hasSupportingEvidence) {
            warnings.add("Missing valid evidence for the correct answer");
        }
        draft.setDuplicateCandidates(toJson(duplicateCandidates));
        draft.setValidationWarnings(toJson(warnings));
        if (!evidenceRequired && !AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_VALID);
        } else if (hasSupportingEvidence
                && !AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_VALID);
        } else if (!hasSupportingEvidence
                && !AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_INVALID);
        }
        if (activeExactDuplicate) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        } else if (!structurallyValid || (evidenceRequired && !hasSupportingEvidence)
                || AiQuestionGenerationDraft.EVIDENCE_INVALID.equals(draft.getEvidenceStatus())
                || AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        } else if (!warnings.isEmpty()) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_WARNING);
        } else {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_VALID);
        }
    }

    private boolean evidenceRequired(UUID batchId) {
        return sourceRepository.findFirstByBatchId(batchId).isPresent();
    }

    private boolean isDraftStructurallyValid(AiQuestionGenerationDraft draft, List<String> warnings) {
        if (draft.getQuestionText() == null || draft.getQuestionText().isBlank()) {
            warnings.add("Question text is required");
            return false;
        }
        List<AiQuestionDraftDtos.AnswerPayload> answers = parseAnswers(draft.getAnswersJson());
        long correctCount = answers.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue).count();
        if ("true_false".equals(draft.getQuestionType())) {
            boolean hasTrue = answers.stream().anyMatch(answer -> "true".equalsIgnoreCase(answer.answerText()));
            boolean hasFalse = answers.stream().anyMatch(answer -> "false".equalsIgnoreCase(answer.answerText()));
            if (correctCount != 1) {
                warnings.add("Exactly one correct answer is required");
                return false;
            }
            if (answers.size() != 2 || !hasTrue || !hasFalse) {
                warnings.add("True/false questions must have exactly True and False answers");
                return false;
            }
        } else if ("single_choice".equals(draft.getQuestionType())) {
            if (answers.size() < 2 || answers.size() > 6) {
                warnings.add("Single choice questions support 2 to 6 answers");
                return false;
            }
            if (correctCount != 1) {
                warnings.add("Exactly one correct answer is required");
                return false;
            }
        } else if ("multiple_choice".equals(draft.getQuestionType())) {
            if (answers.size() < 2 || answers.size() > 6) {
                warnings.add("Multiple choice questions support 2 to 6 answers");
                return false;
            }
            if (correctCount < 2) {
                warnings.add("Multiple choice requires at least two correct answers");
                return false;
            }
        } else {
            warnings.add(SUPPORTED_QUESTION_TYPE_MESSAGE);
            return false;
        }
        return answers.stream().allMatch(answer -> answer.answerText() != null && !answer.answerText().isBlank());
    }

    private List<AiQuestionDraftDtos.DuplicateCandidateResponse> duplicateCandidates(UUID courseId,
            String questionText) {
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> exact = questionRepository
                .findExactDuplicateCandidatesInCourse(courseId, questionText).stream()
                .map(question -> new AiQuestionDraftDtos.DuplicateCandidateResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getStatus().name().toLowerCase(Locale.ROOT),
                        "exact"))
                .toList();
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> near = questionRepository
                .findNearDuplicateCandidatesInCourse(
                        courseId,
                        questionText,
                        NEAR_DUPLICATE_THRESHOLD,
                        NEAR_DUPLICATE_PREFILTER_LIMIT)
                .stream()
                .filter(question -> exact.stream()
                        .noneMatch(candidate -> candidate.questionId().equals(question.getId())))
                .limit(MAX_NEAR_DUPLICATE_CANDIDATES)
                .map(question -> new AiQuestionDraftDtos.DuplicateCandidateResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getStatus().name().toLowerCase(Locale.ROOT),
                        "near"))
                .toList();
        return new ArrayList<>(new LinkedHashSet<>(combine(exact, near))).stream()
                .limit(MAX_NEAR_DUPLICATE_CANDIDATES)
                .toList();
    }

    private <T> List<T> combine(List<T> first, List<T> second) {
        List<T> combined = new ArrayList<>(first);
        combined.addAll(second);
        return combined;
    }

    private void markEvidenceNeedsReview(AiQuestionGenerationDraft draft) {
        List<AiQuestionGenerationEvidence> evidences = evidenceRepository.findByDraftId(draft.getId());
        for (AiQuestionGenerationEvidence evidence : evidences) {
            evidence.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW);
            evidenceRepository.save(evidence);
        }
        draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW);
    }

    private void validateQuota(UUID actorId) {
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long count = batchRepository.countByRequestedByAndCreatedAtAfter(actorId, startOfDay);
        if (count >= properties.getMaxBatchesPerUserDay()) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED, "AI generation daily quota exceeded");
        }
    }

    private UUID validateModuleId(UUID courseId, UUID moduleId) {
        if (moduleId == null)
            return null;
        boolean exists = courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                moduleId,
                courseId,
                CourseModule.STATUS_ACTIVE);
        if (!exists) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Question module must belong to the selected course");
        }
        return moduleId;
    }

    private List<String> normalizeQuestionTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG,
                    "At least one question type is required");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(normalizeQuestionType(value));
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeQuestionType(String value) {
        String normalized = normalizeRequired(value, "Question type is required").replace('-', '_')
                .toLowerCase(Locale.ROOT);
        if (!SUPPORTED_QUESTION_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, SUPPORTED_QUESTION_TYPE_MESSAGE);
        }
        return normalized;
    }

    private List<String> parseQuestionTypesCsv(String csv) {
        if (csv == null || csv.isBlank())
            return List.of("multiple_choice");
        return List.of(csv.split(",")).stream().map(this::normalizeQuestionType).toList();
    }

    private int normalizeRequestedCount(Integer value) {
        if (value == null || value < 1 || value > 20) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG,
                    "Requested count must be between 1 and 20");
        }
        return value;
    }

    private String normalizeLanguage(String value) {
        String normalized = normalizeRequired(value, "Language is required").toLowerCase(Locale.ROOT);
        if (!"vi".equals(normalized) && !"en".equals(normalized)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "Language must be vi or en");
        }
        return normalized;
    }

    private String normalizeInstruction(String value) {
        String normalized = normalizeNullable(value);
        if (normalized != null && normalized.length() > MAX_INSTRUCTION_LENGTH) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG,
                    "Generation instruction must not exceed 2000 characters");
        }
        return normalized;
    }

    private List<AiQuestionDraftDtos.AnswerPayload> normalizeAnswers(List<AiQuestionDraftDtos.AnswerPayload> answers) {
        if (answers == null)
            return List.of();
        List<AiQuestionDraftDtos.AnswerPayload> normalized = new ArrayList<>();
        for (int index = 0; index < answers.size(); index += 1) {
            AiQuestionDraftDtos.AnswerPayload answer = answers.get(index);
            normalized.add(new AiQuestionDraftDtos.AnswerPayload(
                    normalizeRequired(answer.answerText(), "Answer text is required"),
                    answer.correctValue(),
                    answer.orderIndex() == null ? index + 1 : answer.orderIndex()));
        }
        return normalized;
    }

    private boolean correctAnswerChanged(List<AiQuestionDraftDtos.AnswerPayload> previous,
            List<AiQuestionDraftDtos.AnswerPayload> current) {
        String previousCorrect = previous.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue)
                .map(AiQuestionDraftDtos.AnswerPayload::answerText).findFirst().orElse("");
        String currentCorrect = current.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue)
                .map(AiQuestionDraftDtos.AnswerPayload::answerText).findFirst().orElse("");
        return !normalizeForCompare(previousCorrect).equals(normalizeForCompare(currentCorrect));
    }

    private AiQuestionGenerationBatch findBatch(UUID courseId, UUID batchId) {
        AiQuestionGenerationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation batch not found"));
        if (!courseId.equals(batch.getCourseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation batch not found");
        }
        return batch;
    }

    private AiQuestionGenerationDraft findDraftInBatch(AiQuestionGenerationBatch batch, UUID draftId) {
        AiQuestionGenerationDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI draft not found"));
        if (!batch.getId().equals(draft.getBatchId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI draft not found");
        }
        return draft;
    }

    private void ensureVersion(AiQuestionGenerationDraft draft, Integer version) {
        if (version == null || !version.equals(draft.getVersion())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_VERSION_CONFLICT, "Draft has been updated, please reload");
        }
    }

    private void ensureDraftEditable(AiQuestionGenerationDraft draft) {
        if (!AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT.equals(draft.getStatus())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_INVALID, "Only generated drafts can be edited");
        }
    }

    private void refreshBatchCounts(AiQuestionGenerationBatch batch) {
        List<AiQuestionGenerationDraft> drafts = draftRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId());
        batch.setGeneratedCount(drafts.size());
        batch.setUsableCount((int) drafts.stream()
                .filter(draft -> AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT.equals(draft.getStatus()))
                .filter(draft -> !AiQuestionGenerationDraft.VALIDATION_INVALID.equals(draft.getValidationStatus()))
                .count());
        batchRepository.save(batch);
    }

    private AiQuestionDraftDtos.BatchResponse toBatchResponse(AiQuestionGenerationBatch batch) {
        List<AiQuestionDraftDtos.SourceResponse> sources = sourceRepository.findByBatchId(batch.getId()).stream()
                .map(source -> new AiQuestionDraftDtos.SourceResponse(
                        source.getId(),
                        source.getId(),
                        source.getSourceKind(),
                        source.getTranscriptContentId(),
                        source.getLessonId(),
                        source.getSourceName(),
                        source.getSourceChecksum(),
                        source.getSourceVersion(),
                        source.getMimeType(),
                        source.getFileSizeBytes(),
                        source.getNormalizedCharCount(),
                        Boolean.TRUE.equals(source.getDownloadable())))
                .toList();
        List<AiQuestionDraftDtos.DraftResponse> drafts = draftRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())
                .stream()
                .map(this::toDraftResponse)
                .toList();
        return new AiQuestionDraftDtos.BatchResponse(
                batch.getId(),
                batch.getId(),
                batch.getCourseId(),
                batch.getRequestedBy(),
                batch.getStatus(),
                batch.getRequestedCount(),
                batch.getGeneratedCount(),
                batch.getUsableCount(),
                batch.getLanguage(),
                parseQuestionTypesCsv(batch.getRequestedQuestionTypes()),
                batch.getGenerationInstruction(),
                batch.getProvider(),
                batch.getModel(),
                batch.getRetryCount(),
                batch.getErrorCode(),
                batch.getSafeErrorMessage(),
                sources,
                drafts,
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getCompletedAt());
    }

    private AiQuestionDraftDtos.DraftResponse toDraftResponse(AiQuestionGenerationDraft draft) {
        List<AiQuestionGenerationEvidence> evidences = evidenceRepository.findByDraftId(draft.getId());
        return new AiQuestionDraftDtos.DraftResponse(
                draft.getId(),
                draft.getId(),
                draft.getBatchId(),
                draft.getStatus(),
                draft.getValidationStatus(),
                draft.getEvidenceStatus(),
                draft.getVersion(),
                draft.getQuestionText(),
                draft.getQuestionType(),
                draft.getExplanation(),
                draft.getModuleId(),
                parseAnswers(draft.getAnswersJson()),
                parseList(draft.getValidationWarnings(), new TypeReference<List<String>>() {
                }),
                parseList(draft.getDuplicateCandidates(),
                        new TypeReference<List<AiQuestionDraftDtos.DuplicateCandidateResponse>>() {
                        }),
                evidences.stream().map(this::toEvidenceResponse).toList(),
                draft.getCreatedQuestionId(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }

    private AiQuestionDraftDtos.EvidenceResponse toEvidenceResponse(AiQuestionGenerationEvidence evidence) {
        return new AiQuestionDraftDtos.EvidenceResponse(
                evidence.getId(),
                evidence.getGenerationSourceId(),
                evidence.getSourceChunkId(),
                evidence.getChunkReference(),
                evidence.getSourceExcerpt(),
                evidence.getStartMs(),
                evidence.getEndMs(),
                Boolean.TRUE.equals(evidence.getSupportsCorrectAnswer()),
                evidence.getEvidenceStatus(),
                evidence.getReviewerConfirmedBy(),
                evidence.getReviewerConfirmedAt());
    }

    private void recordRevision(UUID draftId, UUID actorId, String before, String after, String changeType) {
        AiQuestionGenerationDraftRevision revision = new AiQuestionGenerationDraftRevision();
        revision.setDraftId(draftId);
        revision.setChangedBy(actorId);
        revision.setBeforeSnapshot(before);
        revision.setAfterSnapshot(after);
        revision.setChangeType(changeType);
        revisionRepository.save(revision);
    }

    private String draftSnapshot(AiQuestionGenerationDraft draft) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", draft.getId());
        snapshot.put("status", draft.getStatus());
        snapshot.put("validationStatus", draft.getValidationStatus());
        snapshot.put("evidenceStatus", draft.getEvidenceStatus());
        snapshot.put("version", draft.getVersion() == null ? 0 : draft.getVersion());
        snapshot.put("questionText", draft.getQuestionText());
        snapshot.put("questionType", draft.getQuestionType());
        snapshot.put("answers", parseAnswers(draft.getAnswersJson()));
        return toJson(snapshot);
    }

    private List<AiQuestionDraftDtos.AnswerPayload> parseAnswers(String json) {
        return parseList(json, new TypeReference<List<AiQuestionDraftDtos.AnswerPayload>>() {
        });
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stored AI draft JSON is invalid");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to serialize AI draft data");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null)
            return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeForCompare(String value) {
        if (value == null)
            return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String messageForSkip(String reasonCode) {
        return switch (reasonCode) {
            case "AI_EXACT_DUPLICATE_ACTIVE" -> "Trùng chính xác với câu hỏi đang active trong course.";
            case "AI_EVIDENCE_REQUIRED" -> "Evidence cần được xác nhận lại trước khi thêm.";
            case "AI_DRAFT_VERSION_CONFLICT" -> "Draft đã được cập nhật, vui lòng tải lại.";
            default -> "Draft không đủ điều kiện để thêm vào course questions.";
        };
    }

}
