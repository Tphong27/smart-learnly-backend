package com.smartlearnly.backend.question.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.SupabaseStorageClient;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSource;
import com.smartlearnly.backend.question.ai.generation.QuestionAiGenerationProperties;
import com.smartlearnly.backend.question.ai.generation.QuestionGenerationProvider;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationBatchRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationDraftRevisionRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationEvidenceRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceChunkRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class AiQuestionDraftServiceTest {

    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CourseModuleRepository courseModuleRepository;
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
    private QuestionAiGenerationProperties properties;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private SupabaseStorageClient supabaseStorageClient;
    @Mock
    private FlashcardDocumentTextExtractionService documentTextExtractionService;
    @Mock
    private VideoAiContentRepository videoAiContentRepository;

    private AiQuestionDraftService service;
    private UUID courseId;
    private UUID batchId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        service = new AiQuestionDraftService(
                courseAccessService,
                currentUserService,
                courseModuleRepository,
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
                storageProperties,
                supabaseStorageClient,
                documentTextExtractionService,
                videoAiContentRepository,
                new ObjectMapper()
        );
        courseId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
    }

    @Test
    void sourceCapabilities_returnsConfiguredLimitsAndRequiresReadableCourse() {
        when(storageProperties.getAiQuestionSourceFileMaxSize())
                .thenReturn(DataSize.ofMegabytes(25));

        AiQuestionDraftDtos.SourceCapabilitiesResponse response =
                service.sourceCapabilities(courseId);

        assertThat(response.minTextCharacters()).isEqualTo(100);
        assertThat(response.maxDocumentBytes()).isEqualTo(DataSize.ofMegabytes(25).toBytes());
        assertThat(response.acceptedDocumentExtensions()).containsExactly("pdf", "docx", "txt");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void listSources_returnsOnlyTranscriptsWithEnoughText() {
        VideoAiContent longTranscript = transcript(
                "This transcript has enough useful characters. ".repeat(4));
        VideoAiContent shortTranscript = transcript("too short");
        when(videoAiContentRepository.findPublishedMasterTranscriptsByCourseId(courseId))
                .thenReturn(List.of(longTranscript, shortTranscript));

        List<AiQuestionDraftDtos.SourceOptionResponse> response =
                service.listSources(courseId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).transcriptContentId()).isEqualTo(longTranscript.getId());
        assertThat(response.get(0).sourceKind()).isEqualTo(AiQuestionGenerationSource.KIND_TRANSCRIPT);
        assertThat(response.get(0).durationSeconds()).isEqualTo(65L);
        assertThat(response.get(0).normalizedCharCount()).isGreaterThanOrEqualTo(100);
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void sourceDownloadUrl_returnsSignedUrl_whenSourceIsDownloadable() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionGenerationSource source = source(batchId, true, "sources/file.txt");
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(storageProperties.getAiQuestionSourceFileBucket()).thenReturn("ai-question-source-files");
        when(supabaseStorageClient.createSignedUrl(
                "ai-question-source-files",
                "sources/file.txt",
                300))
                .thenReturn("https://signed.example.com/file.txt");

        AiQuestionDraftDtos.SourceDownloadUrlResponse response =
                service.sourceDownloadUrl(courseId, batchId, sourceId);

        assertThat(response.url()).isEqualTo("https://signed.example.com/file.txt");
        assertThat(response.fileName()).isEqualTo("source.txt");
        assertThat(response.mimeType()).isEqualTo("text/plain");
        assertThat(response.fileSizeBytes()).isEqualTo(123L);
    }

    @Test
    void sourceDownloadUrl_throwsNotFound_whenSourceBelongsToAnotherBatch() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionGenerationSource source = source(UUID.randomUUID(), true, "sources/file.txt");
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void sourceDownloadUrl_throwsInvalidRequest_whenSourceIsNotDownloadable() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionGenerationSource source = source(batchId, false, null);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private VideoAiContent transcript(String text) {
        VideoAiContent content = new VideoAiContent();
        content.setId(UUID.randomUUID());
        content.setCourseId(courseId);
        content.setLessonId(UUID.randomUUID());
        content.setLanguage("vi");
        content.setTranscriptText(text);
        content.setStatus("published");
        content.setRevision(2L);
        content.setUpdatedAt(Instant.parse("2026-07-28T10:00:00Z"));

        VideoAiTranscriptSegment segment = new VideoAiTranscriptSegment();
        segment.setId(UUID.randomUUID());
        segment.setContent(content);
        segment.setSegmentIndex(1);
        segment.setStartMs(0L);
        segment.setEndMs(65_000L);
        segment.setText(text);
        content.getSegments().add(segment);
        return content;
    }

    private AiQuestionGenerationBatch batch(UUID owningCourseId) {
        AiQuestionGenerationBatch batch = new AiQuestionGenerationBatch();
        batch.setId(batchId);
        batch.setCourseId(owningCourseId);
        batch.setRequestedBy(UUID.randomUUID());
        batch.setStatus(AiQuestionGenerationBatch.STATUS_READY);
        batch.setRequestedQuestionTypes("multiple_choice");
        batch.setRequestedCount(3);
        batch.setGeneratedCount(0);
        batch.setUsableCount(0);
        batch.setLanguage("vi");
        batch.setProvider("gemini");
        batch.setModel("gemini-test");
        batch.setRetryCount(0);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        return batch;
    }

    private AiQuestionGenerationSource source(UUID owningBatchId, boolean downloadable, String payloadRef) {
        AiQuestionGenerationSource source = new AiQuestionGenerationSource();
        source.setId(sourceId);
        source.setBatchId(owningBatchId);
        source.setSourceKind(AiQuestionGenerationSource.KIND_TEMPORARY_FILE);
        source.setSourcePayloadRef(payloadRef);
        source.setDownloadable(downloadable);
        source.setSourceName("source.txt");
        source.setSourceChecksum("checksum");
        source.setSourceVersion("1");
        source.setMimeType("text/plain");
        source.setFileSizeBytes(123L);
        source.setNormalizedCharCount(120);
        return source;
    }
}
