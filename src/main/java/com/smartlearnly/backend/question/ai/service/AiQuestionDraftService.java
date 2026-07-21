package com.smartlearnly.backend.question.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraft;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraftRevision;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationEvidence;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSource;
import com.smartlearnly.backend.question.ai.generation.QuestionAiGenerationProperties;
import com.smartlearnly.backend.question.ai.generation.QuestionGenerationProvider;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationBatchRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRevisionRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationEvidenceRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceRepository;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.question.service.QuestionBankService;
import com.smartlearnly.backend.rag.entity.RagMaterialChunk;
import com.smartlearnly.backend.rag.entity.RagMaterialSnapshot;
import com.smartlearnly.backend.rag.repository.RagMaterialChunkRepository;
import com.smartlearnly.backend.rag.repository.RagMaterialSnapshotRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiQuestionDraftService {
    private static final String PROMPT_TEMPLATE_VERSION = "question-ai-generation-v1";
    private static final String IMPORT_SOURCE_AI_GENERATION = "ai_generation";
    private static final int MAX_INSTRUCTION_LENGTH = 2000;
    private static final int MAX_NEAR_DUPLICATE_CANDIDATES = 3;
    private static final double NEAR_DUPLICATE_THRESHOLD = 0.86D;

    private final QuestionBankService questionBankService;
    private final CurrentUserService currentUserService;
    private final CourseSectionRepository courseSectionRepository;
    private final RagMaterialSnapshotRepository snapshotRepository;
    private final RagMaterialChunkRepository chunkRepository;
    private final AiQuestionGenerationBatchRepository batchRepository;
    private final AiQuestionGenerationSourceRepository sourceRepository;
    private final AiQuestionGenerationDraftRepository draftRepository;
    private final AiQuestionGenerationEvidenceRepository evidenceRepository;
    private final AiQuestionGenerationDraftRevisionRepository revisionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionGenerationProvider generationProvider;
    private final QuestionAiGenerationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.SourceOptionResponse> listSources(UUID bankId) {
        QuestionBank bank = questionBankService.findActiveBankEntity(bankId);
        return snapshotRepository.findReadyByCourseId(bank.getCourseId()).stream()
                .filter(snapshot -> !chunkRepository.findBySnapshotIdOrderByChunkIndexAsc(snapshot.getId()).isEmpty())
                .map(snapshot -> new AiQuestionDraftDtos.SourceOptionResponse(
                        snapshot.getId(),
                        snapshot.getId(),
                        snapshot.getCurriculumLessonResourceId() != null ? snapshot.getCurriculumLessonResourceId() : snapshot.getLessonResourceId(),
                        snapshot.getCourseId(),
                        snapshot.getLessonId(),
                        snapshot.getCurriculumLessonId(),
                        snapshot.getSourceName(),
                        snapshot.getChecksum(),
                        snapshot.getVersion(),
                        snapshot.getStatus(),
                        snapshot.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.BatchResponse> listBatches(UUID bankId) {
        questionBankService.findActiveBankEntity(bankId);
        return batchRepository.findByQuestionBankIdOrderByCreatedAtDesc(bankId).stream()
                .map(this::toBatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiQuestionDraftDtos.BatchResponse getBatch(UUID bankId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.DraftResponse> listDrafts(UUID bankId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
        return draftRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                .map(this::toDraftResponse)
                .toList();
    }

    @Transactional
    public AiQuestionDraftDtos.BatchResponse createBatch(UUID bankId, AiQuestionDraftDtos.CreateBatchRequest request) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        QuestionBank bank = questionBankService.findActiveBankEntity(bankId);
        String idempotencyKey = normalizeRequired(request.idempotencyKey(), "Idempotency key is required");
        var existing = batchRepository.findByRequestedByAndIdempotencyKey(actor.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return toBatchResponse(existing.get());
        }

        validateQuota(actor.getId());
        if (batchRepository.existsByRequestedByAndQuestionBankIdAndStatus(actor.getId(), bankId, AiQuestionGenerationBatch.STATUS_PROCESSING)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "Another AI generation batch is still processing for this question bank");
        }

        List<String> questionTypes = normalizeQuestionTypes(request.questionTypes());
        int requestedCount = normalizeRequestedCount(request.requestedCount());
        String language = normalizeLanguage(request.language());
        String instruction = normalizeInstruction(request.generationInstruction());
        validateModuleId(bank.getCourseId(), request.moduleId());

        List<RagMaterialSnapshot> snapshots = resolveReadySnapshots(bank, request.generationSourceIds());
        Map<UUID, List<RagMaterialChunk>> chunksBySnapshot = resolveChunks(snapshots);

        AiQuestionGenerationBatch batch = new AiQuestionGenerationBatch();
        batch.setQuestionBankId(bank.getId());
        batch.setCourseId(bank.getCourseId());
        batch.setRequestedBy(actor.getId());
        batch.setStatus(AiQuestionGenerationBatch.STATUS_REQUESTED);
        batch.setGenerationInstruction(instruction);
        batch.setInstructionSnapshot(instruction == null ? defaultInstruction() : instruction);
        batch.setRequestedQuestionTypes(String.join(",", questionTypes));
        batch.setRequestedCount(requestedCount);
        batch.setLanguage(language);
        batch.setPromptTemplateVersion(PROMPT_TEMPLATE_VERSION);
        batch.setProvider(generationProvider.providerName());
        batch.setModel(generationProvider.modelName());
        batch.setIdempotencyKey(idempotencyKey);
        batch.setQuotaCharged(true);
        batch = batchRepository.save(batch);

        Map<UUID, AiQuestionGenerationSource> sourceBySnapshot = persistSources(batch, snapshots);

        batch.setStatus(AiQuestionGenerationBatch.STATUS_PROCESSING);
        batch = batchRepository.save(batch);
        generateAndPersistDrafts(batch, request.moduleId(), questionTypes, snapshots, chunksBySnapshot, sourceBySnapshot);
        return toBatchResponse(batchRepository.save(batch));
    }

    @Transactional
    public AiQuestionDraftDtos.BatchResponse retry(UUID bankId, UUID batchId) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
        if (!AiQuestionGenerationBatch.STATUS_FAILED.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.AI_BATCH_NOT_RETRYABLE, "Only FAILED batches can be retried");
        }
        if (batch.getRetryCount() != null && batch.getRetryCount() >= 1) {
            throw new BusinessException(ErrorCode.AI_BATCH_NOT_RETRYABLE, "This batch has already used its retry");
        }
        List<AiQuestionGenerationSource> sources = sourceRepository.findByBatchId(batch.getId());
        List<RagMaterialSnapshot> snapshots = sources.stream()
                .map(source -> snapshotRepository.findById(source.getMaterialSnapshotId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.AI_SOURCE_NOT_RAG_READY, "Source snapshot no longer exists")))
                .toList();
        Map<UUID, List<RagMaterialChunk>> chunksBySnapshot = resolveChunks(snapshots);
        Map<UUID, AiQuestionGenerationSource> sourceBySnapshot = sources.stream()
                .collect(Collectors.toMap(AiQuestionGenerationSource::getMaterialSnapshotId, source -> source));
        batch.setRetryCount((batch.getRetryCount() == null ? 0 : batch.getRetryCount()) + 1);
        batch.setStatus(AiQuestionGenerationBatch.STATUS_PROCESSING);
        batch.setErrorCode(null);
        batch.setSafeErrorMessage(null);
        generateAndPersistDrafts(batch, null, parseQuestionTypesCsv(batch.getRequestedQuestionTypes()), snapshots, chunksBySnapshot, sourceBySnapshot);
        return toBatchResponse(batchRepository.save(batch));
    }

    @Transactional
    public AiQuestionDraftDtos.DraftResponse updateDraft(UUID bankId, UUID batchId, UUID draftId, AiQuestionDraftDtos.UpdateDraftRequest request) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
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

        boolean contentChanged = !normalizeForCompare(previousQuestionText).equals(normalizeForCompare(draft.getQuestionText()))
                || correctAnswerChanged(previousAnswers, request.answers());
        if (contentChanged) {
            markEvidenceNeedsReview(draft);
        }
        applyDraftValidation(batch.getQuestionBankId(), draft, evidenceRepository.findByDraftId(draft.getId()));
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), currentUserService.requireAuthenticatedUser().getId(), before, draftSnapshot(saved), "edited");
        refreshBatchCounts(batch);
        return toDraftResponse(saved);
    }

    @Transactional
    public AiQuestionDraftDtos.DraftResponse rejectDraft(UUID bankId, UUID batchId, UUID draftId, AiQuestionDraftDtos.RejectDraftRequest request) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
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
    public AiQuestionDraftDtos.DraftResponse confirmEvidence(UUID bankId, UUID batchId, UUID draftId, AiQuestionDraftDtos.EvidenceConfirmationRequest request) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
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
        applyDraftValidation(batch.getQuestionBankId(), draft, evidences);
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), actor.getId(), before, draftSnapshot(saved), "evidence_confirmed");
        refreshBatchCounts(batch);
        return toDraftResponse(saved);
    }

    @Transactional
    public AiQuestionDraftDtos.AddSelectedResponse addSelected(UUID bankId, UUID batchId, AiQuestionDraftDtos.AddSelectedRequest request) {
        AiQuestionGenerationBatch batch = findBatch(bankId, batchId);
        QuestionBank bank = questionBankService.findActiveBankEntity(bankId);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<AiQuestionDraftDtos.CreatedQuestion> created = new ArrayList<>();
        List<AiQuestionDraftDtos.SkippedItem> skipped = new ArrayList<>();

        for (AiQuestionDraftDtos.SelectedDraft selected : request.drafts()) {
            AiQuestionGenerationDraft draft = draftRepository.findById(selected.draftId()).orElse(null);
            if (draft == null || !batch.getId().equals(draft.getBatchId())) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(selected.draftId(), "AI_DRAFT_INVALID", "Draft does not belong to this batch"));
                continue;
            }
            if (!selected.version().equals(draft.getVersion())) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(draft.getId(), "AI_DRAFT_VERSION_CONFLICT", "Draft has been updated, please reload"));
                continue;
            }
            String skipReason = acceptBlockReason(bank, draft);
            if (skipReason != null) {
                skipped.add(new AiQuestionDraftDtos.SkippedItem(draft.getId(), skipReason, messageForSkip(skipReason)));
                continue;
            }
            Question question = persistQuestionFromDraft(bank, draft, actor);
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

    private void generateAndPersistDrafts(
            AiQuestionGenerationBatch batch,
            UUID moduleId,
            List<String> questionTypes,
            List<RagMaterialSnapshot> snapshots,
            Map<UUID, List<RagMaterialChunk>> chunksBySnapshot,
            Map<UUID, AiQuestionGenerationSource> sourceBySnapshot
    ) {
        try {
            List<QuestionGenerationProvider.SourceInput> sourceInputs = snapshots.stream()
                    .map(snapshot -> new QuestionGenerationProvider.SourceInput(
                            sourceBySnapshot.get(snapshot.getId()).getId(),
                            snapshot.getSourceName(),
                            snapshot.getChecksum(),
                            snapshot.getVersion(),
                            chunksBySnapshot.get(snapshot.getId()).stream()
                                    .map(chunk -> new QuestionGenerationProvider.ChunkInput(chunk.getId(), chunk.getChunkReference(), chunk.getContentExcerpt()))
                                    .toList()
                    ))
                    .toList();
            QuestionGenerationProvider.GenerationResult result = generationProvider.generate(new QuestionGenerationProvider.GenerationRequest(
                    batch.getId(),
                    batch.getRequestedCount(),
                    questionTypes,
                    batch.getLanguage(),
                    batch.getInstructionSnapshot(),
                    sourceInputs
            ));
            int generated = 0;
            for (QuestionGenerationProvider.GeneratedQuestion generatedQuestion : result.questions()) {
                persistGeneratedDraft(batch, moduleId, generatedQuestion);
                generated += 1;
            }
            batch.setGeneratedCount(generated);
            batch.setUsagePromptTokens(result.promptTokens());
            batch.setUsageCompletionTokens(result.completionTokens());
            batch.setUsageTotalTokens(result.totalTokens());
            batch.setStatus(generated > 0 ? AiQuestionGenerationBatch.STATUS_READY : AiQuestionGenerationBatch.STATUS_FAILED);
            if (generated == 0) {
                batch.setErrorCode(ErrorCode.AI_PROVIDER_OUTPUT_INVALID.name());
                batch.setSafeErrorMessage("AI provider did not return usable draft questions");
            }
            batch.setCompletedAt(Instant.now());
            refreshBatchCounts(batch);
        } catch (BusinessException exception) {
            batch.setStatus(AiQuestionGenerationBatch.STATUS_FAILED);
            batch.setErrorCode(exception.errorCode().name());
            batch.setSafeErrorMessage(exception.getMessage());
            batch.setCompletedAt(Instant.now());
            refreshBatchCounts(batch);
        }
    }

    private void persistGeneratedDraft(AiQuestionGenerationBatch batch, UUID moduleId, QuestionGenerationProvider.GeneratedQuestion generatedQuestion) {
        AiQuestionGenerationDraft draft = new AiQuestionGenerationDraft();
        draft.setBatchId(batch.getId());
        draft.setStatus(AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT);
        draft.setQuestionText(normalizeRequired(generatedQuestion.questionText(), "Generated question text is required"));
        draft.setQuestionType(normalizeQuestionType(generatedQuestion.questionType()));
        draft.setExplanation(normalizeNullable(generatedQuestion.explanation()));
        draft.setModuleId(moduleId);
        draft.setAnswersJson(toJson(normalizeAnswers(generatedQuestion.answers())));
        List<AiQuestionGenerationEvidence> evidences = new ArrayList<>();
        applyDraftValidation(batch.getQuestionBankId(), draft, evidences);
        draft = draftRepository.save(draft);

        for (QuestionGenerationProvider.GeneratedEvidence generatedEvidence : generatedQuestion.evidence() == null ? List.<QuestionGenerationProvider.GeneratedEvidence>of() : generatedQuestion.evidence()) {
            AiQuestionGenerationEvidence evidence = new AiQuestionGenerationEvidence();
            evidence.setDraftId(draft.getId());
            evidence.setGenerationSourceId(generatedEvidence.generationSourceId());
            evidence.setMaterialChunkId(generatedEvidence.chunkId());
            evidence.setChunkReference(normalizeRequired(generatedEvidence.chunkReference(), "Evidence chunk reference is required"));
            evidence.setSourceExcerpt(normalizeRequired(generatedEvidence.excerpt(), "Evidence excerpt is required"));
            evidence.setSupportsCorrectAnswer(generatedEvidence.supportsCorrectAnswer());
            evidence.setEvidenceStatus(generatedEvidence.supportsCorrectAnswer()
                    ? AiQuestionGenerationDraft.EVIDENCE_VALID
                    : AiQuestionGenerationDraft.EVIDENCE_INVALID);
            evidences.add(evidenceRepository.save(evidence));
        }
        applyDraftValidation(batch.getQuestionBankId(), draft, evidences);
        AiQuestionGenerationDraft saved = draftRepository.save(draft);
        recordRevision(saved.getId(), batch.getRequestedBy(), null, draftSnapshot(saved), "generated");
    }

    private String acceptBlockReason(QuestionBank bank, AiQuestionGenerationDraft draft) {
        if (!AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT.equals(draft.getStatus())) {
            return "AI_DRAFT_INVALID";
        }
        if (AiQuestionGenerationDraft.VALIDATION_INVALID.equals(draft.getValidationStatus())) {
            return "AI_DRAFT_INVALID";
        }
        if (!AiQuestionGenerationDraft.EVIDENCE_VALID.equals(draft.getEvidenceStatus())) {
            return "AI_EVIDENCE_REQUIRED";
        }
        if (questionRepository.existsActiveDuplicate(bank.getId(), draft.getQuestionText())) {
            return "AI_EXACT_DUPLICATE_ACTIVE";
        }
        return null;
    }

    private Question persistQuestionFromDraft(QuestionBank bank, AiQuestionGenerationDraft draft, UserAccount actor) {
        Question question = new Question();
        question.setQuestionBankId(bank.getId());
        question.setCourseId(bank.getCourseId());
        question.setModuleId(validateModuleId(bank.getCourseId(), draft.getModuleId()));
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

    private void applyDraftValidation(UUID bankId, AiQuestionGenerationDraft draft, List<AiQuestionGenerationEvidence> evidences) {
        List<String> warnings = new ArrayList<>();
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> duplicateCandidates = duplicateCandidates(bankId, draft.getQuestionText());
        boolean activeExactDuplicate = duplicateCandidates.stream()
                .anyMatch(candidate -> "exact".equals(candidate.matchType()) && !"archived".equals(candidate.status()));
        if (!duplicateCandidates.isEmpty() && !activeExactDuplicate) {
            warnings.add("Potential duplicate question found in this Question Bank");
        }

        boolean structurallyValid = isDraftStructurallyValid(draft, warnings);
        boolean hasSupportingEvidence = evidences.stream().anyMatch(evidence ->
                Boolean.TRUE.equals(evidence.getSupportsCorrectAnswer())
                        && AiQuestionGenerationDraft.EVIDENCE_VALID.equals(evidence.getEvidenceStatus()));
        if (!hasSupportingEvidence) {
            warnings.add("Missing valid evidence for the correct answer");
        }
        draft.setDuplicateCandidates(toJson(duplicateCandidates));
        draft.setValidationWarnings(toJson(warnings));
        if (hasSupportingEvidence && !AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_VALID);
        } else if (!hasSupportingEvidence && !AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_INVALID);
        }
        if (activeExactDuplicate) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        } else if (!structurallyValid || !hasSupportingEvidence || AiQuestionGenerationDraft.EVIDENCE_INVALID.equals(draft.getEvidenceStatus())
                || AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW.equals(draft.getEvidenceStatus())) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        } else if (!warnings.isEmpty()) {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_WARNING);
        } else {
            draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_VALID);
        }
    }

    private boolean isDraftStructurallyValid(AiQuestionGenerationDraft draft, List<String> warnings) {
        if (draft.getQuestionText() == null || draft.getQuestionText().isBlank()) {
            warnings.add("Question text is required");
            return false;
        }
        List<AiQuestionDraftDtos.AnswerPayload> answers = parseAnswers(draft.getAnswersJson());
        long correctCount = answers.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue).count();
        if (correctCount != 1) {
            warnings.add("Exactly one correct answer is required");
            return false;
        }
        if ("true_false".equals(draft.getQuestionType())) {
            boolean hasTrue = answers.stream().anyMatch(answer -> "true".equalsIgnoreCase(answer.answerText()));
            boolean hasFalse = answers.stream().anyMatch(answer -> "false".equalsIgnoreCase(answer.answerText()));
            if (answers.size() != 2 || !hasTrue || !hasFalse) {
                warnings.add("True/false questions must have exactly True and False answers");
                return false;
            }
        } else if ("multiple_choice".equals(draft.getQuestionType())) {
            if (answers.size() < 2 || answers.size() > 6) {
                warnings.add("Multiple choice questions support 2 to 6 answers");
                return false;
            }
        } else {
            warnings.add("Question type must be multiple_choice or true_false");
            return false;
        }
        return answers.stream().allMatch(answer -> answer.answerText() != null && !answer.answerText().isBlank());
    }

    private List<AiQuestionDraftDtos.DuplicateCandidateResponse> duplicateCandidates(UUID bankId, String questionText) {
        String normalizedQuestion = normalizeForCompare(questionText);
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> exact = questionRepository.findExactDuplicateCandidates(bankId, questionText).stream()
                .map(question -> new AiQuestionDraftDtos.DuplicateCandidateResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getStatus().name().toLowerCase(Locale.ROOT),
                        "exact"
                ))
                .toList();
        List<AiQuestionDraftDtos.DuplicateCandidateResponse> near = questionRepository.findByQuestionBankId(bankId).stream()
                .filter(question -> exact.stream().noneMatch(candidate -> candidate.questionId().equals(question.getId())))
                .map(question -> Map.entry(question, similarity(normalizedQuestion, normalizeForCompare(question.getQuestionText()))))
                .filter(entry -> entry.getValue() >= NEAR_DUPLICATE_THRESHOLD)
                .sorted(Map.Entry.<Question, Double>comparingByValue().reversed())
                .limit(MAX_NEAR_DUPLICATE_CANDIDATES)
                .map(entry -> new AiQuestionDraftDtos.DuplicateCandidateResponse(
                        entry.getKey().getId(),
                        entry.getKey().getQuestionText(),
                        entry.getKey().getStatus().name().toLowerCase(Locale.ROOT),
                        "near"
                ))
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

    private Map<UUID, AiQuestionGenerationSource> persistSources(AiQuestionGenerationBatch batch, List<RagMaterialSnapshot> snapshots) {
        Map<UUID, AiQuestionGenerationSource> sourceBySnapshot = new HashMap<>();
        for (RagMaterialSnapshot snapshot : snapshots) {
            AiQuestionGenerationSource source = new AiQuestionGenerationSource();
            source.setBatchId(batch.getId());
            source.setSourceKind(AiQuestionGenerationSource.KIND_MATERIAL);
            source.setMaterialId(snapshot.getCurriculumLessonResourceId() != null ? snapshot.getCurriculumLessonResourceId() : snapshot.getLessonResourceId());
            source.setMaterialSnapshotId(snapshot.getId());
            source.setSourceName(snapshot.getSourceName());
            source.setSourceChecksum(snapshot.getChecksum());
            source.setSourceVersion(snapshot.getVersion());
            source.setRagStatus(snapshot.getStatus());
            sourceBySnapshot.put(snapshot.getId(), sourceRepository.save(source));
        }
        return sourceBySnapshot;
    }

    private List<RagMaterialSnapshot> resolveReadySnapshots(QuestionBank bank, List<UUID> generationSourceIds) {
        if (generationSourceIds == null || generationSourceIds.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "At least one generation source is required");
        }
        List<RagMaterialSnapshot> snapshots = new ArrayList<>();
        for (UUID sourceId : generationSourceIds) {
            RagMaterialSnapshot snapshot = snapshotRepository.findById(sourceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_SOURCE_NOT_RAG_READY, "Generation source was not found or is not RAG-ready"));
            if (!bank.getCourseId().equals(snapshot.getCourseId())) {
                throw new BusinessException(ErrorCode.AI_SOURCE_OUT_OF_SCOPE, "Generation source does not belong to this question bank course");
            }
            if (!snapshot.isReady()) {
                throw new BusinessException(ErrorCode.AI_SOURCE_NOT_RAG_READY, "Generation source is not RAG-ready");
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private Map<UUID, List<RagMaterialChunk>> resolveChunks(List<RagMaterialSnapshot> snapshots) {
        Map<UUID, List<RagMaterialChunk>> chunksBySnapshot = new HashMap<>();
        for (RagMaterialSnapshot snapshot : snapshots) {
            List<RagMaterialChunk> chunks = chunkRepository.findBySnapshotIdOrderByChunkIndexAsc(snapshot.getId());
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_SOURCE_NOT_RAG_READY, "Generation source has no stable chunks");
            }
            chunksBySnapshot.put(snapshot.getId(), chunks);
        }
        return chunksBySnapshot;
    }

    private void validateQuota(UUID actorId) {
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long count = batchRepository.countByRequestedByAndCreatedAtAfter(actorId, startOfDay);
        if (count >= properties.getMaxBatchesPerUserDay()) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED, "AI generation daily quota exceeded");
        }
    }

    private UUID validateModuleId(UUID courseId, UUID moduleId) {
        if (moduleId == null) return null;
        boolean exists = courseSectionRepository.findByIdAndCourseId(moduleId, courseId).isPresent();
        if (!exists) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question module must belong to the selected course");
        }
        return moduleId;
    }

    private List<String> normalizeQuestionTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "At least one question type is required");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(normalizeQuestionType(value));
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeQuestionType(String value) {
        String normalized = normalizeRequired(value, "Question type is required").replace('-', '_').toLowerCase(Locale.ROOT);
        if (!"multiple_choice".equals(normalized) && !"true_false".equals(normalized)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "Question type must be multiple_choice or true_false");
        }
        return normalized;
    }

    private List<String> parseQuestionTypesCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of("multiple_choice");
        return List.of(csv.split(",")).stream().map(this::normalizeQuestionType).toList();
    }

    private int normalizeRequestedCount(Integer value) {
        if (value == null || (value != 5 && value != 10 && value != 20)) {
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "Requested count must be 5, 10, or 20");
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
            throw new BusinessException(ErrorCode.AI_INVALID_GENERATION_CONFIG, "Generation instruction must not exceed 2000 characters");
        }
        return normalized;
    }

    private String defaultInstruction() {
        return "Generate grounded draft questions from only the selected RAG-ready lesson materials.";
    }

    private List<AiQuestionDraftDtos.AnswerPayload> normalizeAnswers(List<AiQuestionDraftDtos.AnswerPayload> answers) {
        if (answers == null) return List.of();
        List<AiQuestionDraftDtos.AnswerPayload> normalized = new ArrayList<>();
        for (int index = 0; index < answers.size(); index += 1) {
            AiQuestionDraftDtos.AnswerPayload answer = answers.get(index);
            normalized.add(new AiQuestionDraftDtos.AnswerPayload(
                    normalizeRequired(answer.answerText(), "Answer text is required"),
                    answer.correctValue(),
                    answer.orderIndex() == null ? index + 1 : answer.orderIndex()
            ));
        }
        return normalized;
    }

    private boolean correctAnswerChanged(List<AiQuestionDraftDtos.AnswerPayload> previous, List<AiQuestionDraftDtos.AnswerPayload> current) {
        String previousCorrect = previous.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue).map(AiQuestionDraftDtos.AnswerPayload::answerText).findFirst().orElse("");
        String currentCorrect = current.stream().filter(AiQuestionDraftDtos.AnswerPayload::correctValue).map(AiQuestionDraftDtos.AnswerPayload::answerText).findFirst().orElse("");
        return !normalizeForCompare(previousCorrect).equals(normalizeForCompare(currentCorrect));
    }

    private AiQuestionGenerationBatch findBatch(UUID bankId, UUID batchId) {
        AiQuestionGenerationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation batch not found"));
        if (!bankId.equals(batch.getQuestionBankId())) {
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
                        source.getMaterialId(),
                        source.getMaterialSnapshotId(),
                        source.getSourceName(),
                        source.getSourceChecksum(),
                        source.getSourceVersion(),
                        source.getRagStatus()
                ))
                .toList();
        List<AiQuestionDraftDtos.DraftResponse> drafts = draftRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                .map(this::toDraftResponse)
                .toList();
        return new AiQuestionDraftDtos.BatchResponse(
                batch.getId(),
                batch.getId(),
                batch.getQuestionBankId(),
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
                batch.getCompletedAt()
        );
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
                parseList(draft.getValidationWarnings(), new TypeReference<List<String>>() {}),
                parseList(draft.getDuplicateCandidates(), new TypeReference<List<AiQuestionDraftDtos.DuplicateCandidateResponse>>() {}),
                evidences.stream().map(this::toEvidenceResponse).toList(),
                draft.getCreatedQuestionId(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private AiQuestionDraftDtos.EvidenceResponse toEvidenceResponse(AiQuestionGenerationEvidence evidence) {
        return new AiQuestionDraftDtos.EvidenceResponse(
                evidence.getId(),
                evidence.getGenerationSourceId(),
                evidence.getMaterialChunkId(),
                evidence.getChunkReference(),
                evidence.getSourceExcerpt(),
                Boolean.TRUE.equals(evidence.getSupportsCorrectAnswer()),
                evidence.getEvidenceStatus(),
                evidence.getReviewerConfirmedBy(),
                evidence.getReviewerConfirmedAt()
        );
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
        return parseList(json, new TypeReference<List<AiQuestionDraftDtos.AnswerPayload>>() {});
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
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeForCompare(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private double similarity(String a, String b) {
        if (a.isBlank() || b.isBlank()) return 0D;
        Set<String> left = Set.of(a.split("\\s+"));
        Set<String> right = Set.of(b.split("\\s+"));
        long intersection = left.stream().filter(right::contains).count();
        long union = new LinkedHashSet<String>() {{
            addAll(left);
            addAll(right);
        }}.size();
        return union == 0 ? 0D : (double) intersection / union;
    }

    private String messageForSkip(String reasonCode) {
        return switch (reasonCode) {
            case "AI_EXACT_DUPLICATE_ACTIVE" -> "Trùng chính xác với câu hỏi đang active trong Question Bank.";
            case "AI_EVIDENCE_REQUIRED" -> "Evidence cần được xác nhận lại trước khi thêm.";
            case "AI_DRAFT_VERSION_CONFLICT" -> "Draft đã được cập nhật, vui lòng tải lại.";
            default -> "Draft không đủ điều kiện để thêm vào Question Bank.";
        };
    }
}
