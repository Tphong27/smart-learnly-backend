package com.smartlearnly.backend.question.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraft;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraftRevision;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationEvidence;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSource;
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
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiQuestionDraftServiceTest {
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AiQuestionGenerationBatchRepository batchRepository;
    @Mock
    private AiQuestionGenerationSourceRepository sourceRepository;
    @Mock
    private AiQuestionGenerationDraftRepository draftRepository;
    @Mock
    private AiQuestionGenerationEvidenceRepository evidenceRepository;
    @Mock
    private AiQuestionGenerationDraftRevisionRepository revisionRepository;
    @Mock
    private AiQuestionGenerationSourceChunkRepository sourceChunkRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository answerRepository;
    @Mock
    private QuestionGenerationProvider generationProvider;
    @Mock
    private AiQuestionSourceService sourceService;
    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionAiGenerationProperties properties = new QuestionAiGenerationProperties();
    private final List<AiQuestionGenerationBatch> batches = new ArrayList<>();
    private final List<AiQuestionGenerationDraft> drafts = new ArrayList<>();
    private final List<AiQuestionGenerationSource> sources = new ArrayList<>();
    private final List<AiQuestionGenerationEvidence> evidences = new ArrayList<>();
    private final List<AiQuestionGenerationDraftRevision> revisions = new ArrayList<>();
    private final List<Question> savedQuestions = new ArrayList<>();
    private final List<QuestionAnswer> savedAnswers = new ArrayList<>();

    private AiQuestionDraftService service;
    private UUID courseId;
    private UUID actorId;
    private UserAccount actor;

    @BeforeEach
    void setUp() {
        service = new AiQuestionDraftService(
                courseAccessService,
                currentUserService,
                batchRepository,
                sourceRepository,
                draftRepository,
                evidenceRepository,
                revisionRepository,
                sourceChunkRepository,
                questionRepository,
                answerRepository,
                generationProvider,
                properties,
                sourceService,
                objectMapper);
        service.setNotificationService(notificationService);
        courseId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        actor = new UserAccount();
        actor.setId(actorId);
        properties.setMaxBatchesPerUserDay(5);
        lenient().when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        lenient().when(generationProvider.providerName()).thenReturn("gemini");
        lenient().when(generationProvider.modelName()).thenReturn("gemini-test");
        installRepositoryFakes();
    }

    @Test
    void listBatches_mapsSourcesDraftsEvidenceAndRequiresReadableCourse() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), true);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "What is Java?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationEvidence evidence = evidence(draft.getId(), source.getId(), null, true);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);
        evidences.add(evidence);

        List<AiQuestionDraftDtos.BatchResponse> result = service.listBatches(courseId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sources()).hasSize(1);
        assertThat(result.get(0).drafts()).hasSize(1);
        assertThat(result.get(0).drafts().get(0).evidences()).hasSize(1);
        assertThat(result.get(0).questionTypes()).containsExactly("multiple_choice", "true_false");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void getBatchAndListDrafts_throwNotFoundWhenBatchMissingOrCourseDiffers() {
        UUID batchId = UUID.randomUUID();
        assertThatThrownBy(() -> service.getBatch(courseId, batchId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        AiQuestionGenerationBatch otherCourseBatch = batch(AiQuestionGenerationBatch.STATUS_READY);
        otherCourseBatch.setCourseId(UUID.randomUUID());
        batches.add(otherCourseBatch);

        assertThatThrownBy(() -> service.listDrafts(courseId, otherCourseBatch.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getBatch_returnsExistingBatchWithBlankStoredQuestionTypesDefaulted() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        batch.setRequestedQuestionTypes(" ");
        batches.add(batch);

        AiQuestionDraftDtos.BatchResponse response = service.getBatch(courseId, batch.getId());

        assertThat(response.batchId()).isEqualTo(batch.getId());
        assertThat(response.questionTypes()).containsExactly("multiple_choice");
    }

    @Test
    void createBatch_returnsExistingBatchForSameRequesterAndIdempotencyKey() {
        AiQuestionGenerationBatch existing = batch(AiQuestionGenerationBatch.STATUS_READY);
        existing.setIdempotencyKey("same-key");
        batches.add(existing);
        when(batchRepository.findByRequestedByAndIdempotencyKey(actorId, "same-key")).thenReturn(Optional.of(existing));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("same-key"));

        assertThat(response.batchId()).isEqualTo(existing.getId());
        verify(generationProvider, never()).generate(any());
    }

    @Test
    void createBatch_generatesValidDraftsWithoutSourcesAndEmitsReadyNotification() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedMc("What is encapsulation?"), generatedTrueFalse()),
                11,
                22,
                33));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("new-key"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        assertThat(response.generatedCount()).isEqualTo(2);
        assertThat(response.usableCount()).isEqualTo(2);
        assertThat(response.drafts()).extracting(AiQuestionDraftDtos.DraftResponse::validationStatus)
                .containsOnly(AiQuestionGenerationDraft.VALIDATION_VALID);
        ArgumentCaptor<QuestionGenerationProvider.GenerationRequest> providerRequest =
                ArgumentCaptor.forClass(QuestionGenerationProvider.GenerationRequest.class);
        verify(generationProvider).generate(providerRequest.capture());
        assertThat(providerRequest.getValue().questionTypes()).containsExactly("multiple_choice", "true_false");
        assertThat(providerRequest.getValue().generationInstruction())
                .isEqualTo("Generate grounded draft questions from only the provided source content.");
        ArgumentCaptor<NotificationCreateCommand> notification =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService).emit(notification.capture());
        assertThat(notification.getValue().title()).isEqualTo("AI question drafts are ready");
    }

    @Test
    void createBatch_persistsAtMostRequestedCountWhenProviderReturnsExtraQuestions() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(
                        generatedMc("Question 1?"),
                        generatedMc("Question 2?"),
                        generatedMc("Question 3?")),
                11,
                22,
                33));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("cap-key"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        assertThat(response.generatedCount()).isEqualTo(2);
        assertThat(response.drafts()).extracting(AiQuestionDraftDtos.DraftResponse::questionText)
                .containsExactly("Question 1?", "Question 2?");
        assertThat(drafts).hasSize(2);
    }

    @Test
    void createBatch_persistsGeneratedEvidenceFromSourceChunks() {
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        QuestionGenerationProvider.SourceInput input = new QuestionGenerationProvider.SourceInput(
                sourceId,
                "Lesson transcript",
                "checksum",
                "1",
                List.of(new QuestionGenerationProvider.ChunkInput(chunkId, "00:00-00:10", "Core concept excerpt")));
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenAnswer(invocation -> {
            AiQuestionGenerationBatch batch = invocation.getArgument(1);
            AiQuestionGenerationSource source = source(batch.getId(), false);
            source.setId(sourceId);
            sources.add(source);
            return List.of(input);
        });
        AiQuestionGenerationSourceChunk chunk = chunk(sourceId, chunkId);
        when(sourceChunkRepository.findById(chunkId)).thenReturn(Optional.of(chunk));
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(new QuestionGenerationProvider.GeneratedQuestion(
                        "Which answer is supported by the transcript?",
                        "multiple_choice",
                        answers("Supported answer", "Distractor"),
                        "Because the transcript says so.",
                        List.of(new QuestionGenerationProvider.GeneratedEvidence(
                                sourceId,
                                chunkId,
                                "00:00-00:10",
                                "Core concept excerpt",
                                true)))),
                1,
                2,
                3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("source-key"));

        AiQuestionGenerationBatch seed = batches.get(0);
        assertThat(seed.getStatus()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        assertThat(response.drafts()).hasSize(1);
        assertThat(response.drafts().get(0).evidences()).hasSize(1);
        assertThat(response.drafts().get(0).evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(evidences.get(0).getStartMs()).isEqualTo(1_000L);
        assertThat(evidences.get(0).getEndMs()).isEqualTo(10_000L);
    }

    @Test
    void createBatch_keepsEvidenceEvenWhenSourceChunkIsMissingAndEvidenceDoesNotSupportAnswer() {
        UUID sourceId = UUID.randomUUID();
        UUID missingChunkId = UUID.randomUUID();
        QuestionGenerationProvider.SourceInput input = new QuestionGenerationProvider.SourceInput(
                sourceId,
                "Lesson transcript",
                "checksum",
                "1",
                List.of(new QuestionGenerationProvider.ChunkInput(missingChunkId, "chunk", "excerpt")));
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenAnswer(invocation -> {
            AiQuestionGenerationBatch batch = invocation.getArgument(1);
            AiQuestionGenerationSource source = source(batch.getId(), false);
            source.setId(sourceId);
            sources.add(source);
            return List.of(input);
        });
        when(sourceChunkRepository.findById(missingChunkId)).thenReturn(Optional.empty());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(new QuestionGenerationProvider.GeneratedQuestion(
                        "Unsupported evidence?",
                        "multiple_choice",
                        answers("A", "B"),
                        null,
                        List.of(new QuestionGenerationProvider.GeneratedEvidence(
                                sourceId,
                                missingChunkId,
                                "chunk",
                                "excerpt",
                                false)))),
                1,
                2,
                3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("missing-chunk"));

        assertThat(response.drafts().get(0).validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_INVALID);
        assertThat(response.drafts().get(0).evidences().get(0).sourceChunkId()).isNull();
        assertThat(response.drafts().get(0).evidences().get(0).supportsCorrectAnswer()).isFalse();
    }

    @Test
    void createBatch_marksDraftInvalidWhenSourcesRequireEvidenceButProviderEvidenceIsNull() {
        UUID sourceId = UUID.randomUUID();
        QuestionGenerationProvider.SourceInput input = new QuestionGenerationProvider.SourceInput(
                sourceId,
                "Source",
                "checksum",
                "1",
                List.of(new QuestionGenerationProvider.ChunkInput(UUID.randomUUID(), "chunk", "excerpt")));
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenAnswer(invocation -> {
            AiQuestionGenerationBatch batch = invocation.getArgument(1);
            AiQuestionGenerationSource source = source(batch.getId(), false);
            source.setId(sourceId);
            sources.add(source);
            return List.of(input);
        });
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(new QuestionGenerationProvider.GeneratedQuestion(
                        "Missing evidence list?",
                        "multiple_choice",
                        answers("A", "B"),
                        null,
                        null)),
                1,
                2,
                3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("null-evidence"));

        assertThat(response.drafts().get(0).evidences()).isEmpty();
        assertThat(response.drafts().get(0).validationWarnings()).contains("Missing valid evidence for the correct answer");
    }

    @Test
    void createBatch_marksFailedWhenProviderReturnsNoQuestions() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(List.of(), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("empty-key"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_FAILED);
        assertThat(response.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_OUTPUT_INVALID.name());
        assertThat(response.safeErrorMessage()).isEqualTo("AI provider did not return usable draft questions");
    }

    @Test
    void createBatch_catchesBusinessExceptionFromProviderAndStoresSafeFailure() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "Provider timed out"));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("failed-key"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_FAILED);
        assertThat(response.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE.name());
        assertThat(response.safeErrorMessage()).isEqualTo("Provider timed out");
    }

    @Test
    void createBatch_ignoresLegacyModuleAndCreatesCourseWideDraft() {
        UUID moduleId = UUID.randomUUID();
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedMc("Module question?")), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId,
                new AiQuestionDraftDtos.CreateBatchRequest(
                        List.of(),
                        List.of(),
                        List.of("multiple_choice"),
                        1,
                        moduleId,
                        "en",
                        "Use concise English wording.",
                        "module-key"));

        assertThat(response.generationInstruction()).isEqualTo("Use concise English wording.");
        assertThat(response.drafts().get(0).moduleId()).isNull();
        ArgumentCaptor<NotificationCreateCommand> notification =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService).emit(notification.capture());
        assertThat(notification.getValue().actionUrl()).isEqualTo(
                "/admin/courses/" + courseId + "/questions/ai-drafts/" + response.batchId());
        assertThat(notification.getValue().payload()).doesNotContainKey("moduleId");
    }

    @Test
    void createBatch_skipsNotificationWhenNotificationServiceIsAbsent() {
        service.setNotificationService(null);
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedMc("Silent notification?")), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("no-notification"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        verify(notificationService, never()).emit(any());
    }

    @Test
    void createBatch_failedNotificationUsesFallbackBodyWhenSafeMessageIsNull() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, null));

        service.createBatch(courseId, request("null-safe-message"));

        ArgumentCaptor<NotificationCreateCommand> notification =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService).emit(notification.capture());
        assertThat(notification.getValue().title()).isEqualTo("AI question generation failed");
        assertThat(notification.getValue().body()).isEqualTo("The AI provider did not return usable draft questions.");
    }

    @Test
    void createBatch_rejectsInvalidQuotaProcessingAndGenerationConfig() {
        properties.setMaxBatchesPerUserDay(5);
        when(batchRepository.countByRequestedByAndCreatedAtAfter(eq(actorId), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.createBatch(courseId, request("quota-key")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED));

        when(batchRepository.countByRequestedByAndCreatedAtAfter(eq(actorId), any())).thenReturn(0L);
        when(batchRepository.existsByRequestedByAndCourseIdAndStatus(actorId, courseId, AiQuestionGenerationBatch.STATUS_PROCESSING))
                .thenReturn(true);
        assertThatThrownBy(() -> service.createBatch(courseId, request("processing-key")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_INVALID_GENERATION_CONFIG));

        when(batchRepository.existsByRequestedByAndCourseIdAndStatus(actorId, courseId, AiQuestionGenerationBatch.STATUS_PROCESSING))
                .thenReturn(false);
        assertInvalidCreate(requestWithTypes("null-types", null), "At least one question type is required");
        assertInvalidCreate(requestWithTypes("bad-key", List.of()), "At least one question type is required");
        assertInvalidCreate(requestWithTypes("bad-type-key", List.of("essay")), "Question type must be single_choice, multiple_choice, or true_false");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), null, null, "vi", null, "null-count"),
                "Requested count must be between 1 and 20");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), 0, null, "vi", null, "bad-count"),
                "Requested count must be between 1 and 20");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), 21, null, "vi", null, "too-many"),
                "Requested count must be between 1 and 20");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), 1, null, "jp", null, "bad-lang"),
                "Language must be vi or en");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), 1, null, "vi", "x".repeat(2001), "long-instruction"),
                "Generation instruction must not exceed 2000 characters");
        assertInvalidCreate(new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), List.of("multiple_choice"), 1, null, "vi", null, " "),
                "Idempotency key is required");
    }

    @Test
    void retry_allowsOneFailedBatchRetryAndUsesStoredSourceInputs() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_FAILED);
        batch.setRetryCount(null);
        batch.setRequestedQuestionTypes(null);
        batch.setErrorCode("OLD");
        batch.setSafeErrorMessage("old");
        batches.add(batch);
        when(sourceService.buildSourceInputsForBatch(batch.getId())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedMc("Retry question?")), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.retry(courseId, batch.getId());

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.errorCode()).isNull();
    }

    @Test
    void retry_allowsZeroRetryCountAndStoredCsvTypes() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_FAILED);
        batch.setRetryCount(0);
        batch.setRequestedQuestionTypes("true_false");
        batches.add(batch);
        when(sourceService.buildSourceInputsForBatch(batch.getId())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedTrueFalse()), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.retry(courseId, batch.getId());

        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.questionTypes()).containsExactly("true_false");
    }

    @Test
    void retry_doesNotEmitNotificationWhenRequesterIsMissing() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_FAILED);
        batch.setRequestedBy(null);
        batches.add(batch);
        when(sourceService.buildSourceInputsForBatch(batch.getId())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(generatedMc("Missing requester?")), 1, 2, 3));

        AiQuestionDraftDtos.BatchResponse response = service.retry(courseId, batch.getId());

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_READY);
        verify(notificationService, never()).emit(any());
    }

    @Test
    void retry_rejectsNonFailedOrAlreadyRetriedBatches() {
        AiQuestionGenerationBatch ready = batch(AiQuestionGenerationBatch.STATUS_READY);
        batches.add(ready);
        assertThatThrownBy(() -> service.retry(courseId, ready.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_BATCH_NOT_RETRYABLE));

        AiQuestionGenerationBatch failed = batch(AiQuestionGenerationBatch.STATUS_FAILED);
        failed.setRetryCount(1);
        batches.add(failed);
        assertThatThrownBy(() -> service.retry(courseId, failed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_BATCH_NOT_RETRYABLE));
    }

    @Test
    void updateDraft_updatesTextAnswersKeepsEvidenceValidAndRecordsRevision() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), false);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Old question?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationEvidence evidence = evidence(draft.getId(), source.getId(), null, true);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);
        evidences.add(evidence);

        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(
                        1,
                        "New question?",
                        "Updated explanation",
                        null,
                        answers("New correct", "Other")));

        assertThat(response.questionText()).isEqualTo("New question?");
        assertThat(response.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_VALID);
        assertThat(evidences.get(0).getEvidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(revisions).extracting(AiQuestionGenerationDraftRevision::getChangeType).contains("edited");
    }

    @Test
    void updateDraft_keepsNoSourceDraftValidAfterContentChanges() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Old question?", "multiple_choice", validAnswersJson());
        draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW);
        draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        batches.add(batch);
        drafts.add(draft);

        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(
                        1,
                        "Updated question?",
                        "Updated explanation",
                        null,
                        answers("New correct", "Other")));

        assertThat(response.questionText()).isEqualTo("Updated question?");
        assertThat(response.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_VALID);
    }

    @Test
    void updateDraft_keepsEvidenceWhenOnlyExplanationChangesAndFlagsNearDuplicateWarning() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Explain polymorphism clearly", "multiple_choice", validAnswersJson());
        Question nearDuplicate = question("explain polymorphism clearly", QuestionStatus.DRAFT);
        batches.add(batch);
        drafts.add(draft);
        when(questionRepository.findNearDuplicateCandidatesInCourse(
                eq(courseId),
                any(),
                anyDouble(),
                anyInt())).thenReturn(List.of(nearDuplicate));

        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(
                        1,
                        "  explain   polymorphism clearly ",
                        "Only explanation changed",
                        null,
                        answers("A", "B")));

        assertThat(response.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_WARNING);
        assertThat(response.duplicateCandidates()).extracting(AiQuestionDraftDtos.DuplicateCandidateResponse::matchType)
                .containsExactly("near");
    }

    @Test
    void updateDraft_flagsExactActiveDuplicateAsInvalid() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Duplicate active?", "multiple_choice", validAnswersJson());
        batches.add(batch);
        drafts.add(draft);
        when(questionRepository.findExactDuplicateCandidatesInCourse(courseId, "Duplicate active?"))
                .thenReturn(List.of(question("Duplicate active?", QuestionStatus.APPROVED)));

        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(1, "Duplicate active?", null, null, answers("A", "B")));

        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_INVALID);
        assertThat(response.duplicateCandidates()).extracting(AiQuestionDraftDtos.DuplicateCandidateResponse::matchType)
                .containsExactly("exact");
    }

    @Test
    void updateDraft_keepsEvidenceValidWhenCorrectAnswerChanges() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), false);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Same question?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationEvidence evidence = evidence(draft.getId(), source.getId(), null, true);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);
        evidences.add(evidence);

        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(
                        1,
                        "Same question?",
                        null,
                        null,
                        List.of(
                                new AiQuestionDraftDtos.AnswerPayload("A", false, 1),
                                new AiQuestionDraftDtos.AnswerPayload("B", true, 2),
                                new AiQuestionDraftDtos.AnswerPayload("C", true, 3))));

        assertThat(response.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_VALID);
    }

    @Test
    void updateDraft_rejectsMissingOrForeignDrafts() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        batches.add(batch);

        assertThatThrownBy(() -> service.updateDraft(courseId, batch.getId(), UUID.randomUUID(),
                new AiQuestionDraftDtos.UpdateDraftRequest(1, "Missing?", null, null, answers("A", "B"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        AiQuestionGenerationDraft foreign = draft(UUID.randomUUID(), "Foreign?", "multiple_choice", validAnswersJson());
        drafts.add(foreign);
        assertThatThrownBy(() -> service.updateDraft(courseId, batch.getId(), foreign.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(1, "Foreign?", null, null, answers("A", "B"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void updateDraft_marksStructuralValidationErrors() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft blankQuestion = draft(batch.getId(), "Original?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationDraft noCorrect = draft(batch.getId(), "No correct?", "multiple_choice",
                "[{\"answerText\":\"A\",\"correct\":false,\"orderIndex\":1},{\"answerText\":\"B\",\"correct\":false,\"orderIndex\":2}]");
        AiQuestionGenerationDraft badTrueFalse = draft(batch.getId(), "Bad true false?", "true_false",
                "[{\"answerText\":\"True\",\"correct\":true,\"orderIndex\":1},{\"answerText\":\"Maybe\",\"correct\":false,\"orderIndex\":2}]");
        AiQuestionGenerationDraft tooFewMultipleChoice = draft(batch.getId(), "Too few?", "multiple_choice",
                "[{\"answerText\":\"Only\",\"correct\":true,\"orderIndex\":1}]");
        AiQuestionGenerationDraft unsupportedType = draft(batch.getId(), "Unsupported?", "matching", validAnswersJson());
        batches.add(batch);
        drafts.addAll(List.of(blankQuestion, noCorrect, badTrueFalse, tooFewMultipleChoice, unsupportedType));

        assertThatThrownBy(() -> service.updateDraft(courseId, batch.getId(), blankQuestion.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(1, " ", null, null, answers("A", "B"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertInvalidAfterNoopUpdate(batch, noCorrect, answers("A", "B").stream()
                .map(answer -> new AiQuestionDraftDtos.AnswerPayload(answer.answerText(), false, answer.orderIndex()))
                .toList(), "Multiple choice requires at least two correct answers");
        assertInvalidAfterNoopUpdate(batch, badTrueFalse, List.of(
                new AiQuestionDraftDtos.AnswerPayload("True", true, 1),
                new AiQuestionDraftDtos.AnswerPayload("Maybe", false, 2)), "True/false questions must have exactly True and False answers");
        assertInvalidAfterNoopUpdate(batch, tooFewMultipleChoice,
                List.of(new AiQuestionDraftDtos.AnswerPayload("Only", true, 1)), "Multiple choice questions support 2 to 6 answers");
        assertInvalidAfterNoopUpdate(batch, unsupportedType, answers("A", "B"), "Question type must be single_choice, multiple_choice, or true_false");
    }

    @Test
    void updateRejectAndConfirmRejectVersionOrNonEditableDrafts() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Question?", "multiple_choice", validAnswersJson());
        batches.add(batch);
        drafts.add(draft);

        assertThatThrownBy(() -> service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(2, "Question?", null, null, answers("A", "B"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_DRAFT_VERSION_CONFLICT));
        assertThatThrownBy(() -> service.confirmEvidence(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.EvidenceConfirmationRequest(null, true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_DRAFT_VERSION_CONFLICT));

        draft.setStatus(AiQuestionGenerationDraft.STATUS_ACCEPTED);
        assertThatThrownBy(() -> service.rejectDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.RejectDraftRequest(1, "bad", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_DRAFT_INVALID));
    }

    @Test
    void rejectDraft_marksReviewedAndRefreshesBatchCounts() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Question?", "multiple_choice", validAnswersJson());
        batches.add(batch);
        drafts.add(draft);

        AiQuestionDraftDtos.DraftResponse response = service.rejectDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.RejectDraftRequest(1, "low_quality", "Needs work"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationDraft.STATUS_REJECTED);
        assertThat(draft.getReviewedBy()).isEqualTo(actorId);
        assertThat(batch.getUsableCount()).isZero();
        assertThat(revisions).extracting(AiQuestionGenerationDraftRevision::getChangeType).contains("rejected");
    }

    @Test
    void confirmEvidence_canValidateOrInvalidateEvidence() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), false);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Question?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationEvidence evidence = evidence(draft.getId(), source.getId(), null, true);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);
        evidences.add(evidence);

        AiQuestionDraftDtos.DraftResponse valid = service.confirmEvidence(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.EvidenceConfirmationRequest(1, true));
        assertThat(valid.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_VALID);
        assertThat(valid.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_VALID);

        draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_VALID);
        AiQuestionDraftDtos.DraftResponse invalid = service.confirmEvidence(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.EvidenceConfirmationRequest(1, false));
        assertThat(invalid.evidenceStatus()).isEqualTo(AiQuestionGenerationDraft.EVIDENCE_INVALID);
        assertThat(invalid.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_INVALID);
    }

    @Test
    void addSelected_createsQuestionsAndSkipsInvalidSelectionsWithReasons() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft accepted = draft(batch.getId(), "Accepted?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationDraft wrongVersion = draft(batch.getId(), "Wrong version?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationDraft invalidValidation = draft(batch.getId(), "Invalid?", "multiple_choice", validAnswersJson());
        invalidValidation.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_INVALID);
        AiQuestionGenerationDraft invalidStatus = draft(batch.getId(), "Accepted already?", "multiple_choice", validAnswersJson());
        invalidStatus.setStatus(AiQuestionGenerationDraft.STATUS_ACCEPTED);
        AiQuestionGenerationDraft duplicate = draft(batch.getId(), "Duplicate?", "multiple_choice", validAnswersJson());
        batches.add(batch);
        drafts.addAll(List.of(accepted, wrongVersion, invalidValidation, invalidStatus, duplicate));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Duplicate?", null)).thenReturn(true);

        AiQuestionDraftDtos.AddSelectedResponse response = service.addSelected(courseId, batch.getId(),
                new AiQuestionDraftDtos.AddSelectedRequest(List.of(
                        new AiQuestionDraftDtos.SelectedDraft(UUID.randomUUID(), 1),
                        new AiQuestionDraftDtos.SelectedDraft(wrongVersion.getId(), 2),
                        new AiQuestionDraftDtos.SelectedDraft(invalidValidation.getId(), 1),
                        new AiQuestionDraftDtos.SelectedDraft(invalidStatus.getId(), 1),
                        new AiQuestionDraftDtos.SelectedDraft(duplicate.getId(), 1),
                        new AiQuestionDraftDtos.SelectedDraft(accepted.getId(), 1)), "add-key"));

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).draftId()).isEqualTo(accepted.getId());
        assertThat(savedQuestions.get(0).getQuestionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(savedQuestions.get(0).getStatus()).isEqualTo(QuestionStatus.DRAFT);
        assertThat(savedAnswers).hasSize(2);
        assertThat(response.skippedItems()).extracting(AiQuestionDraftDtos.SkippedItem::reasonCode)
                .containsExactly("AI_DRAFT_INVALID", "AI_DRAFT_VERSION_CONFLICT", "AI_DRAFT_INVALID", "AI_DRAFT_INVALID", "AI_EXACT_DUPLICATE_ACTIVE");
    }

    @Test
    void addSelected_skipsForeignDraftAndDefaultsAnswerOrderIndexes() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft accepted = draft(batch.getId(), "Defaults order?", "multiple_choice",
                "[{\"answerText\":\"A\",\"correct\":true,\"orderIndex\":null},{\"answerText\":\"B\",\"correct\":false,\"orderIndex\":null}]");
        AiQuestionGenerationDraft foreign = draft(UUID.randomUUID(), "Foreign batch?", "multiple_choice", validAnswersJson());
        batches.add(batch);
        drafts.addAll(List.of(accepted, foreign));

        AiQuestionDraftDtos.AddSelectedResponse response = service.addSelected(courseId, batch.getId(),
                new AiQuestionDraftDtos.AddSelectedRequest(List.of(
                        new AiQuestionDraftDtos.SelectedDraft(foreign.getId(), 1),
                        new AiQuestionDraftDtos.SelectedDraft(accepted.getId(), 1)), "add-foreign"));

        assertThat(response.skippedItems()).extracting(AiQuestionDraftDtos.SkippedItem::reasonCode)
                .containsExactly("AI_DRAFT_INVALID");
        assertThat(savedAnswers).extracting(QuestionAnswer::getOrderIndex).containsExactly(1, 2);
    }

    @Test
    void addSelected_skipsWhenEvidenceIsRequiredButNotValid() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), false);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Needs evidence?", "multiple_choice", validAnswersJson());
        draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_NEEDS_REVIEW);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);

        AiQuestionDraftDtos.AddSelectedResponse response = service.addSelected(courseId, batch.getId(),
                new AiQuestionDraftDtos.AddSelectedRequest(
                        List.of(new AiQuestionDraftDtos.SelectedDraft(draft.getId(), 1)), "add-evidence"));

        assertThat(response.created()).isEmpty();
        assertThat(response.skippedItems()).extracting(AiQuestionDraftDtos.SkippedItem::reasonCode)
                .containsExactly("AI_EVIDENCE_REQUIRED");
    }

    @Test
    void addSelected_acceptsDraftWithValidEvidenceWhenEvidenceIsRequired() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationSource source = source(batch.getId(), false);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Evidence accepted?", "multiple_choice", validAnswersJson());
        AiQuestionGenerationEvidence evidence = evidence(draft.getId(), source.getId(), null, true);
        batches.add(batch);
        sources.add(source);
        drafts.add(draft);
        evidences.add(evidence);

        AiQuestionDraftDtos.AddSelectedResponse response = service.addSelected(courseId, batch.getId(),
                new AiQuestionDraftDtos.AddSelectedRequest(
                        List.of(new AiQuestionDraftDtos.SelectedDraft(draft.getId(), 1)), "add-valid-evidence"));

        assertThat(response.created()).hasSize(1);
        assertThat(response.skippedItems()).isEmpty();
    }

    @Test
    void createBatch_invalidGeneratedQuestionsBecomeFailedBatches() {
        when(sourceService.persistAndBuildSourceInputs(eq(courseId), any(), any(), any())).thenReturn(List.of());
        when(generationProvider.generate(any())).thenReturn(new QuestionGenerationProvider.GenerationResult(
                List.of(new QuestionGenerationProvider.GeneratedQuestion("Essay?", "essay", answers("A", "B"), null, List.of())),
                null,
                null,
                null));

        AiQuestionDraftDtos.BatchResponse response = service.createBatch(courseId, request("bad-output"));

        assertThat(response.status()).isEqualTo(AiQuestionGenerationBatch.STATUS_FAILED);
        assertThat(response.errorCode()).isEqualTo(ErrorCode.AI_INVALID_GENERATION_CONFIG.name());
    }

    @Test
    void listDrafts_throwsInternalErrorWhenStoredJsonIsInvalid() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Broken?", "multiple_choice", "{not-json");
        batches.add(batch);
        drafts.add(draft);

        assertThatThrownBy(() -> service.listDrafts(courseId, batch.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void listDrafts_treatsBlankStoredJsonListsAsEmpty() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Blank json lists?", "multiple_choice", validAnswersJson());
        draft.setValidationWarnings(" ");
        draft.setDuplicateCandidates("");
        batches.add(batch);
        drafts.add(draft);

        List<AiQuestionDraftDtos.DraftResponse> response = service.listDrafts(courseId, batch.getId());

        assertThat(response.get(0).validationWarnings()).isEmpty();
        assertThat(response.get(0).duplicateCandidates()).isEmpty();
    }

    @Test
    void listDrafts_treatsNullStoredJsonListsAsEmpty() {
        AiQuestionGenerationBatch batch = batch(AiQuestionGenerationBatch.STATUS_READY);
        AiQuestionGenerationDraft draft = draft(batch.getId(), "Null json lists?", "multiple_choice", validAnswersJson());
        draft.setValidationWarnings(null);
        draft.setDuplicateCandidates(null);
        batches.add(batch);
        drafts.add(draft);

        List<AiQuestionDraftDtos.DraftResponse> response = service.listDrafts(courseId, batch.getId());

        assertThat(response.get(0).validationWarnings()).isEmpty();
        assertThat(response.get(0).duplicateCandidates()).isEmpty();
    }

    private void assertInvalidCreate(AiQuestionDraftDtos.CreateBatchRequest request, String message) {
        assertThatThrownBy(() -> service.createBatch(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isIn(ErrorCode.INVALID_REQUEST, ErrorCode.AI_INVALID_GENERATION_CONFIG);
                    assertThat(exception.getMessage()).contains(message);
                });
    }

    private void assertInvalidAfterNoopUpdate(
            AiQuestionGenerationBatch batch,
            AiQuestionGenerationDraft draft,
            List<AiQuestionDraftDtos.AnswerPayload> answers,
            String warning
    ) {
        AiQuestionDraftDtos.DraftResponse response = service.updateDraft(courseId, batch.getId(), draft.getId(),
                new AiQuestionDraftDtos.UpdateDraftRequest(
                        1,
                        draft.getQuestionText(),
                        draft.getExplanation(),
                        null,
                        answers));
        assertThat(response.validationStatus()).isEqualTo(AiQuestionGenerationDraft.VALIDATION_INVALID);
        assertThat(response.validationWarnings()).contains(warning);
    }

    private void installRepositoryFakes() {
        lenient().when(batchRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return batches.stream().filter(batch -> id.equals(batch.getId())).findFirst();
        });
        lenient().when(batchRepository.findByCourseIdOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return batches.stream().filter(batch -> id.equals(batch.getCourseId())).toList();
        });
        lenient().when(batchRepository.findByRequestedByAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(batchRepository.countByRequestedByAndCreatedAtAfter(any(), any())).thenReturn(0L);
        lenient().when(batchRepository.existsByRequestedByAndCourseIdAndStatus(any(), any(), any())).thenReturn(false);
        lenient().when(batchRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            if (batch.getGeneratedCount() == null) {
                batch.setGeneratedCount(0);
            }
            if (batch.getUsableCount() == null) {
                batch.setUsableCount(0);
            }
            if (batch.getRetryCount() == null) {
                batch.setRetryCount(0);
            }
            if (batch.getCreatedAt() == null) {
                batch.setCreatedAt(Instant.parse("2026-08-05T01:00:00Z"));
            }
            batch.setUpdatedAt(Instant.parse("2026-08-05T01:01:00Z"));
            batches.removeIf(existing -> existing.getId().equals(batch.getId()));
            batches.add(batch);
            return batch;
        });
        lenient().when(draftRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return drafts.stream().filter(draft -> id.equals(draft.getId())).findFirst();
        });
        lenient().when(draftRepository.findByBatchIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return drafts.stream().filter(draft -> id.equals(draft.getBatchId())).toList();
        });
        lenient().when(draftRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationDraft draft = invocation.getArgument(0);
            if (draft.getId() == null) {
                draft.setId(UUID.randomUUID());
            }
            if (draft.getVersion() == null) {
                draft.setVersion(1);
            }
            if (draft.getCreatedAt() == null) {
                draft.setCreatedAt(Instant.parse("2026-08-05T02:00:00Z"));
            }
            draft.setUpdatedAt(Instant.parse("2026-08-05T02:01:00Z"));
            drafts.removeIf(existing -> existing.getId().equals(draft.getId()));
            drafts.add(draft);
            return draft;
        });
        lenient().when(sourceRepository.findByBatchId(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return sources.stream().filter(source -> id.equals(source.getBatchId())).toList();
        });
        lenient().when(sourceRepository.findFirstByBatchId(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return sources.stream().filter(source -> id.equals(source.getBatchId())).findFirst();
        });
        lenient().when(evidenceRepository.findByDraftId(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return evidences.stream().filter(evidence -> id.equals(evidence.getDraftId())).toList();
        });
        lenient().when(evidenceRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationEvidence evidence = invocation.getArgument(0);
            if (evidence.getId() == null) {
                evidence.setId(UUID.randomUUID());
            }
            evidences.removeIf(existing -> existing.getId().equals(evidence.getId()));
            evidences.add(evidence);
            return evidence;
        });
        lenient().when(revisionRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationDraftRevision revision = invocation.getArgument(0);
            if (revision.getId() == null) {
                revision.setId(UUID.randomUUID());
            }
            revisions.add(revision);
            return revision;
        });
        lenient().when(questionRepository.findExactDuplicateCandidatesInCourse(any(), any())).thenReturn(List.of());
        lenient().when(questionRepository.findNearDuplicateCandidatesInCourse(
                any(),
                any(),
                anyDouble(),
                anyInt())).thenReturn(List.of());
        lenient().when(questionRepository.existsActiveDuplicateInCourse(any(), any(), any())).thenReturn(false);
        lenient().when(questionRepository.save(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(UUID.randomUUID());
            savedQuestions.add(question);
            return question;
        });
        lenient().when(answerRepository.save(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(UUID.randomUUID());
            savedAnswers.add(answer);
            return answer;
        });
    }

    private AiQuestionDraftDtos.CreateBatchRequest request(String key) {
        return new AiQuestionDraftDtos.CreateBatchRequest(
                List.of(),
                List.of(),
                List.of("multiple-choice", "true_false", "multiple_choice"),
                2,
                null,
                "VI",
                " ",
                key);
    }

    private AiQuestionDraftDtos.CreateBatchRequest requestWithTypes(String key, List<String> types) {
        return new AiQuestionDraftDtos.CreateBatchRequest(List.of(), List.of(), types, 1, null, "vi", null, key);
    }

    private QuestionGenerationProvider.GeneratedQuestion generatedMc(String questionText) {
        return new QuestionGenerationProvider.GeneratedQuestion(
                questionText,
                "multiple_choice",
                answers("A", "B"),
                "Explanation",
                List.of());
    }

    private QuestionGenerationProvider.GeneratedQuestion generatedTrueFalse() {
        return new QuestionGenerationProvider.GeneratedQuestion(
                "Java is a programming language.",
                "true_false",
                List.of(
                        new AiQuestionDraftDtos.AnswerPayload("True", true, 1),
                        new AiQuestionDraftDtos.AnswerPayload("False", false, 2)),
                null,
                List.of());
    }

    private List<AiQuestionDraftDtos.AnswerPayload> answers(String correct, String incorrect) {
        return List.of(
                new AiQuestionDraftDtos.AnswerPayload(correct, true, null),
                new AiQuestionDraftDtos.AnswerPayload(incorrect, true, null));
    }

    private String validAnswersJson() {
        return """
                [{"answerText":"A","correct":true,"orderIndex":1},{"answerText":"B","correct":true,"orderIndex":2}]
                """;
    }

    private AiQuestionGenerationBatch batch(String status) {
        AiQuestionGenerationBatch batch = new AiQuestionGenerationBatch();
        batch.setId(UUID.randomUUID());
        batch.setCourseId(courseId);
        batch.setRequestedBy(actorId);
        batch.setStatus(status);
        batch.setGenerationInstruction(null);
        batch.setInstructionSnapshot("instruction");
        batch.setRequestedQuestionTypes("multiple_choice,true_false");
        batch.setRequestedCount(2);
        batch.setGeneratedCount(0);
        batch.setUsableCount(0);
        batch.setLanguage("vi");
        batch.setPromptTemplateVersion("question-ai-generation-v1");
        batch.setProvider("gemini");
        batch.setModel("gemini-test");
        batch.setIdempotencyKey(UUID.randomUUID().toString());
        batch.setRetryCount(0);
        batch.setQuotaCharged(true);
        batch.setCreatedAt(Instant.parse("2026-08-05T00:00:00Z"));
        batch.setUpdatedAt(Instant.parse("2026-08-05T00:01:00Z"));
        return batch;
    }

    private AiQuestionGenerationDraft draft(UUID batchId, String text, String type, String answersJson) {
        AiQuestionGenerationDraft draft = new AiQuestionGenerationDraft();
        draft.setId(UUID.randomUUID());
        draft.setBatchId(batchId);
        draft.setStatus(AiQuestionGenerationDraft.STATUS_GENERATED_DRAFT);
        draft.setValidationStatus(AiQuestionGenerationDraft.VALIDATION_VALID);
        draft.setEvidenceStatus(AiQuestionGenerationDraft.EVIDENCE_VALID);
        draft.setVersion(1);
        draft.setQuestionText(text);
        draft.setQuestionType(type);
        draft.setExplanation("Explanation");
        draft.setModuleId(null);
        draft.setAnswersJson(answersJson);
        draft.setValidationWarnings("[]");
        draft.setDuplicateCandidates("[]");
        draft.setCreatedAt(Instant.parse("2026-08-05T00:02:00Z"));
        draft.setUpdatedAt(Instant.parse("2026-08-05T00:03:00Z"));
        return draft;
    }

    private AiQuestionGenerationSource source(UUID batchId, boolean downloadable) {
        AiQuestionGenerationSource source = new AiQuestionGenerationSource();
        source.setId(UUID.randomUUID());
        source.setBatchId(batchId);
        source.setSourceKind(AiQuestionGenerationSource.KIND_TRANSCRIPT);
        source.setTranscriptContentId(UUID.randomUUID());
        source.setLessonId(UUID.randomUUID());
        source.setDownloadable(downloadable);
        source.setSourceName("Transcript");
        source.setSourceChecksum("checksum");
        source.setSourceVersion("1");
        source.setMimeType("text/plain");
        source.setFileSizeBytes(100L);
        source.setNormalizedCharCount(300);
        source.setCreatedAt(Instant.parse("2026-08-05T00:04:00Z"));
        return source;
    }

    private AiQuestionGenerationSourceChunk chunk(UUID sourceId, UUID chunkId) {
        AiQuestionGenerationSourceChunk chunk = new AiQuestionGenerationSourceChunk();
        chunk.setId(chunkId);
        chunk.setGenerationSourceId(sourceId);
        chunk.setChunkIndex(1);
        chunk.setChunkReference("00:00-00:10");
        chunk.setContentExcerpt("Core concept excerpt");
        chunk.setContentChecksum("chunk-checksum");
        chunk.setStartMs(1_000L);
        chunk.setEndMs(10_000L);
        return chunk;
    }

    private AiQuestionGenerationEvidence evidence(UUID draftId, UUID sourceId, UUID chunkId, boolean supports) {
        AiQuestionGenerationEvidence evidence = new AiQuestionGenerationEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDraftId(draftId);
        evidence.setGenerationSourceId(sourceId);
        evidence.setSourceChunkId(chunkId);
        evidence.setChunkReference("00:00-00:10");
        evidence.setSourceExcerpt("Evidence excerpt");
        evidence.setSupportsCorrectAnswer(supports);
        evidence.setEvidenceStatus(supports ? AiQuestionGenerationDraft.EVIDENCE_VALID : AiQuestionGenerationDraft.EVIDENCE_INVALID);
        evidence.setCreatedAt(Instant.parse("2026-08-05T00:05:00Z"));
        return evidence;
    }

    private Question question(String text, QuestionStatus status) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setCourseId(courseId);
        question.setQuestionText(text);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setStatus(status);
        return question;
    }
}
