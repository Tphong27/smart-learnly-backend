package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.user.repository.UserRepository;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.generation.VideoLearningAidGenerationService;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VideoAiJobWorkerConcurrencyTest {
    private VideoAiProperties properties;
    private VideoAiJobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private ScheduledExecutorService leaseScheduler;

    @BeforeEach
    void setUp() {
        properties = new VideoAiProperties();
        properties.setEnabled(true);
        properties.setLeaseDuration(Duration.ofMinutes(3));
        jobRepository = mock(VideoAiJobRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        leaseScheduler = mock(ScheduledExecutorService.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void dispatchDoesNotClaimAnotherJobWhileLocalWorkerPermitIsHeld() {
        CapturingExecutor executor = new CapturingExecutor();
        VideoAiJob job = claimableJob();
        when(jobRepository.findClaimable(any(Instant.class), any(Pageable.class))).thenReturn(List.of(job));
        when(jobRepository.save(any(VideoAiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VideoAiJobWorker worker = worker(executor);

        worker.dispatch();
        worker.dispatch();

        verify(jobRepository, times(1)).findClaimable(any(Instant.class), any(Pageable.class));
        assertThat(executor.task).isNotNull();
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLeaseOwner()).isNotNull();
        assertThat(job.getLeaseHeartbeatAt()).isNotNull();
    }

    @Test
    void rejectedDispatchReleasesLeaseAndRestoresAttemptCount() {
        UUID jobId = UUID.randomUUID();
        VideoAiJob job = claimableJob();
        job.setId(jobId);
        when(jobRepository.findClaimable(any(Instant.class), any(Pageable.class))).thenReturn(List.of(job));
        when(jobRepository.save(any(VideoAiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.findOwnedForUpdate(eq(jobId), any(UUID.class))).thenReturn(Optional.of(job));
        VideoAiJobWorker worker = worker(command -> {
            throw new RejectedExecutionException("executor stopping");
        });

        worker.dispatch();

        assertThat(job.getStatus()).isEqualTo("pending");
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getLeaseExpiresAt()).isNull();
        assertThat(job.getLeaseHeartbeatAt()).isNull();
    }

    @Test
    void heartbeatSucceedsOnlyWhileRepositoryStillOwnsLease() {
        UUID jobId = UUID.randomUUID();
        UUID leaseOwner = UUID.randomUUID();
        VideoAiJobWorker worker = worker(Runnable::run);
        when(jobRepository.extendLease(eq(jobId), eq(leaseOwner), any(Instant.class), any(Instant.class)))
                .thenReturn(1, 0);

        assertThat(worker.heartbeat(jobId, leaseOwner)).isTrue();
        assertThat(worker.heartbeat(jobId, leaseOwner)).isFalse();
    }

    @Test
    void failedDatabaseClaimReleasesLocalPermitForNextDispatch() {
        CapturingExecutor executor = new CapturingExecutor();
        VideoAiJob job = claimableJob();
        when(jobRepository.findClaimable(any(Instant.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(job));
        when(jobRepository.save(any(VideoAiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VideoAiJobWorker worker = worker(executor);

        worker.dispatch();
        worker.dispatch();

        verify(jobRepository, times(2)).findClaimable(any(Instant.class), any(Pageable.class));
        assertThat(executor.task).isNotNull();
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    private VideoAiJob claimableJob() {
        VideoAiJob job = new VideoAiJob();
        job.setId(UUID.randomUUID());
        job.setStatus("pending");
        job.setStage("queued");
        job.setAttemptCount(0);
        job.setProgressPercent(0);
        job.setNextAttemptAt(Instant.now());
        return job;
    }

    private VideoAiJobWorker worker(Executor executor) {
        return new VideoAiJobWorker(
                properties,
                new HlsProperties(),
                jobRepository,
                mock(VideoAiContentRepository.class),
                mock(HlsLessonRepository.class),
                mock(CloudflareR2StorageClient.class),
                mock(VideoTranscriptionService.class),
                mock(VideoLearningAidGenerationService.class),
                new ObjectMapper(),
                mock(FlashcardDocumentGenerationService.class),
                mock(FlashcardSetRepository.class),
                mock(FlashcardStagingBatchRepository.class),
                mock(FlashcardStagingCardRepository.class),
                mock(CourseRepository.class),
                mock(UserRepository.class),
                executor,
                leaseScheduler,
                transactionManager);
    }

    private static final class CapturingExecutor implements Executor {
        private Runnable task;

        @Override
        public void execute(Runnable command) {
            task = command;
        }
    }
}
