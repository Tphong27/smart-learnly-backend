package com.smartlearnly.backend.question.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.service.AiQuestionDraftService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CourseAiQuestionDraftControllerTest {

    @Mock
    private AiQuestionDraftService aiQuestionDraftService;

    private CourseAiQuestionDraftController controller;
    private UUID courseId;
    private UUID batchId;
    private UUID draftId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        controller = new CourseAiQuestionDraftController(aiQuestionDraftService);
        courseId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        draftId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
    }

    @Test
    void sourceCapabilities_returnsServiceResponse() {
        AiQuestionDraftDtos.SourceCapabilitiesResponse capabilities =
                new AiQuestionDraftDtos.SourceCapabilitiesResponse(100, 50_000, 1024, 200_000, 8, 300_000, List.of("text/plain"), List.of("txt"));
        when(aiQuestionDraftService.sourceCapabilities(courseId)).thenReturn(capabilities);

        var response = controller.sourceCapabilities(courseId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(capabilities);
    }

    @Test
    void sources_returnsServiceResponse() {
        AiQuestionDraftDtos.SourceOptionResponse source =
                new AiQuestionDraftDtos.SourceOptionResponse(sourceId, sourceId, courseId, null, null, "transcript", "Video transcript", null, "vi", 60L, "checksum", "1", 1, 120, null);
        when(aiQuestionDraftService.listSources(courseId)).thenReturn(List.of(source));

        var response = controller.sources(courseId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(source);
    }

    @Test
    void create_returnsCreatedBatchLocation() {
        AiQuestionDraftDtos.CreateBatchRequest request = createRequest();
        AiQuestionDraftDtos.BatchResponse batch = batch();
        when(aiQuestionDraftService.createBatch(courseId, request)).thenReturn(batch);

        var response = controller.create(courseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/api/v1/admin/courses/" + courseId + "/questions/ai-drafts/" + batchId);
        assertThat(response.getBody().data()).isSameAs(batch);
    }

    @Test
    void createMultipart_passesEmptyFileList_whenFilesAreNull() {
        AiQuestionDraftDtos.CreateBatchRequest request = createRequest();
        AiQuestionDraftDtos.BatchResponse batch = batch();
        when(aiQuestionDraftService.createBatch(courseId, request, List.of())).thenReturn(batch);

        var response = controller.createMultipart(courseId, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data()).isSameAs(batch);
    }

    @Test
    void list_returnsBatchesFromService() {
        AiQuestionDraftDtos.BatchResponse batch = batch();
        when(aiQuestionDraftService.listBatches(courseId)).thenReturn(List.of(batch));

        var response = controller.list(courseId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(batch);
    }

    @Test
    void get_returnsBatchFromService() {
        AiQuestionDraftDtos.BatchResponse batch = batch();
        when(aiQuestionDraftService.getBatch(courseId, batchId)).thenReturn(batch);

        var response = controller.get(courseId, batchId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(batch);
    }

    @Test
    void items_returnsDraftsFromService() {
        AiQuestionDraftDtos.DraftResponse draft = draft();
        when(aiQuestionDraftService.listDrafts(courseId, batchId)).thenReturn(List.of(draft));

        var response = controller.items(courseId, batchId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(draft);
    }

    @Test
    void sourceDownloadUrl_returnsSignedUrlFromService() {
        AiQuestionDraftDtos.SourceDownloadUrlResponse signedUrl =
                new AiQuestionDraftDtos.SourceDownloadUrlResponse("https://signed.example.com", null, "source.txt", "text/plain", 123L);
        when(aiQuestionDraftService.sourceDownloadUrl(courseId, batchId, sourceId)).thenReturn(signedUrl);

        var response = controller.sourceDownloadUrl(courseId, batchId, sourceId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(signedUrl);
    }

    @Test
    void updateDraft_returnsUpdatedDraftFromService() {
        AiQuestionDraftDtos.UpdateDraftRequest request = updateRequest();
        AiQuestionDraftDtos.DraftResponse draft = draft();
        when(aiQuestionDraftService.updateDraft(courseId, batchId, draftId, request)).thenReturn(draft);

        var response = controller.updateDraft(courseId, batchId, draftId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(draft);
    }

    @Test
    void rejectDraft_returnsRejectedDraftFromService() {
        AiQuestionDraftDtos.RejectDraftRequest request =
                new AiQuestionDraftDtos.RejectDraftRequest(1, "low_quality", "Needs rewrite");
        AiQuestionDraftDtos.DraftResponse draft = draft();
        when(aiQuestionDraftService.rejectDraft(courseId, batchId, draftId, request)).thenReturn(draft);

        var response = controller.rejectDraft(courseId, batchId, draftId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(draft);
    }

    @Test
    void confirmEvidence_returnsUpdatedDraftFromService() {
        AiQuestionDraftDtos.EvidenceConfirmationRequest request =
                new AiQuestionDraftDtos.EvidenceConfirmationRequest(1, true);
        AiQuestionDraftDtos.DraftResponse draft = draft();
        when(aiQuestionDraftService.confirmEvidence(courseId, batchId, draftId, request)).thenReturn(draft);

        var response = controller.confirmEvidence(courseId, batchId, draftId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(draft);
    }

    @Test
    void addSelected_returnsProcessingResultFromService() {
        AiQuestionDraftDtos.AddSelectedRequest request =
                new AiQuestionDraftDtos.AddSelectedRequest(List.of(new AiQuestionDraftDtos.SelectedDraft(draftId, 1)), "idem");
        AiQuestionDraftDtos.AddSelectedResponse result =
                new AiQuestionDraftDtos.AddSelectedResponse(List.of(), List.of());
        when(aiQuestionDraftService.addSelected(courseId, batchId, request)).thenReturn(result);

        var response = controller.addSelected(courseId, batchId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(result);
    }

    @Test
    void retry_returnsBatchFromService() {
        AiQuestionDraftDtos.BatchResponse batch = batch();
        when(aiQuestionDraftService.retry(courseId, batchId)).thenReturn(batch);

        var response = controller.retry(courseId, batchId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(batch);
    }

    private AiQuestionDraftDtos.CreateBatchRequest createRequest() {
        return new AiQuestionDraftDtos.CreateBatchRequest(
                List.of(sourceId),
                List.of(),
                List.of("multiple_choice"),
                3,
                null,
                "vi",
                null,
                "idem");
    }

    private AiQuestionDraftDtos.UpdateDraftRequest updateRequest() {
        return new AiQuestionDraftDtos.UpdateDraftRequest(
                1,
                "Question?",
                "Explanation",
                null,
                List.of(
                        new AiQuestionDraftDtos.AnswerPayload("A", true, 1),
                        new AiQuestionDraftDtos.AnswerPayload("B", false, 2)));
    }

    private AiQuestionDraftDtos.BatchResponse batch() {
        return new AiQuestionDraftDtos.BatchResponse(
                batchId,
                batchId,
                courseId,
                UUID.randomUUID(),
                "ready",
                3,
                0,
                0,
                "vi",
                List.of("multiple_choice"),
                null,
                "gemini",
                "gemini-test",
                0,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private AiQuestionDraftDtos.DraftResponse draft() {
        return new AiQuestionDraftDtos.DraftResponse(
                draftId,
                draftId,
                batchId,
                "generated_draft",
                "valid",
                "valid",
                1,
                "Question?",
                "multiple_choice",
                "Explanation",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);
    }
}
