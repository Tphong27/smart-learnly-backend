package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.user.repository.UserRepository;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.generation.VideoLearningAidGenerationService;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionResult;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionSegment;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VideoAiJobWorkerTranscriptTest {

    @Test
    void automaticTranscriptJobStopsBeforeGenerativeSuggestions() {
        VideoAiProperties properties = new VideoAiProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(1);
        properties.setLeaseDuration(Duration.ofMinutes(3));
        HlsProperties hlsProperties = new HlsProperties();
        hlsProperties.setAiAudioBucket("private-ai-audio");
        VideoAiJobRepository jobRepository = mock(VideoAiJobRepository.class);
        VideoAiContentRepository contentRepository = mock(VideoAiContentRepository.class);
        HlsLessonRepository hlsRepository = mock(HlsLessonRepository.class);
        VideoTranscriptionService transcriptionService = mock(VideoTranscriptionService.class);
        VideoLearningAidGenerationService generationService = mock(VideoLearningAidGenerationService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        doReturn(heartbeat).when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));

        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        VideoAiJob job = new VideoAiJob();
        job.setId(UUID.randomUUID());
        job.setLessonId(lessonId);
        job.setLessonScope("MASTER");
        job.setCourseId(UUID.randomUUID());
        job.setSourceVersion(sourceVersion);
        job.setJobType("VIDEO_TRANSCRIPT");
        job.setSourceLanguage("auto");
        job.setRequestedBy(UUID.randomUUID());
        job.setStatus("pending");
        job.setStage("queued");
        job.setProgressPercent(0);
        job.setAttemptCount(0);
        job.setNextAttemptAt(Instant.now());
        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setHlsStatus("ready");
        hls.setProcessingJobId(sourceVersion);
        hls.setAiAudioObjectKey("hls/lesson/source/ai/source.mp3");
        AtomicReference<VideoAiContent> savedContent = new AtomicReference<>();

        when(jobRepository.findClaimable(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(jobRepository.save(any(VideoAiJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.findOwnedForUpdate(eq(job.getId()), any(UUID.class)))
                .thenReturn(Optional.of(job));
        when(hlsRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        when(transcriptionService.transcribe(any(Path.class), eq("auto")))
                .thenReturn(new TranscriptionResult(
                        "en",
                        0.99,
                        2_000,
                        List.of(new TranscriptionSegment(0, 0, 2_000, "Transcript is ready."))));
        when(contentRepository.saveAndFlush(any(VideoAiContent.class))).thenAnswer(invocation -> {
            VideoAiContent content = invocation.getArgument(0);
            content.setId(UUID.randomUUID());
            savedContent.set(content);
            return content;
        });
        when(contentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(savedContent.get()));

        VideoAiJobWorker worker = new VideoAiJobWorker(
                properties,
                hlsProperties,
                jobRepository,
                contentRepository,
                hlsRepository,
                mock(CloudflareR2StorageClient.class),
                transcriptionService,
                generationService,
                new ObjectMapper(),
                mock(FlashcardDocumentGenerationService.class),
                mock(FlashcardSetRepository.class),
                mock(FlashcardStagingBatchRepository.class),
                mock(FlashcardStagingCardRepository.class),
                mock(CourseRepository.class),
                mock(UserRepository.class),
                Runnable::run,
                scheduler,
                transactionManager);

        worker.dispatch();

        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getStage()).isEqualTo("transcript_ready");
        assertThat(job.getProgressPercent()).isEqualTo(100);
        assertThat(savedContent.get()).isNotNull();
        assertThat(savedContent.get().getTranscriptText()).isEqualTo("Transcript is ready.");
        verify(generationService, never()).generate(any(), any());
    }
}
