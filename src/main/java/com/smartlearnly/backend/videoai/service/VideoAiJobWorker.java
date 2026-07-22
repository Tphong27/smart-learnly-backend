package com.smartlearnly.backend.videoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.entity.VideoAiChapter;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import com.smartlearnly.backend.videoai.generation.VideoLearningAidGenerationService;
import com.smartlearnly.backend.videoai.generation.VideoLearningAidGenerationService.LearningAidResult;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionResult;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionSegment;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@EnableScheduling
public class VideoAiJobWorker {
    private static final String VIDEO_TRANSCRIPT = "VIDEO_TRANSCRIPT";
    private final VideoAiProperties properties;
    private final HlsProperties hlsProperties;
    private final VideoAiJobRepository jobRepository;
    private final VideoAiContentRepository contentRepository;
    private final HlsLessonRepository hlsLessonRepository;
    private final CloudflareR2StorageClient r2StorageClient;
    private final VideoTranscriptionService transcriptionService;
    private final VideoLearningAidGenerationService generationService;
    private final ObjectMapper objectMapper;
    private final FlashcardDocumentGenerationService flashcardGenerationService;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardStagingBatchRepository stagingBatchRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final Executor executor;
    private final ScheduledExecutorService leaseScheduler;
    private final TransactionTemplate transactions;
    private final AtomicBoolean workerPermit = new AtomicBoolean();

    public VideoAiJobWorker(
            VideoAiProperties properties,
            HlsProperties hlsProperties,
            VideoAiJobRepository jobRepository,
            VideoAiContentRepository contentRepository,
            HlsLessonRepository hlsLessonRepository,
            CloudflareR2StorageClient r2StorageClient,
            VideoTranscriptionService transcriptionService,
            VideoLearningAidGenerationService generationService,
            ObjectMapper objectMapper,
            FlashcardDocumentGenerationService flashcardGenerationService,
            FlashcardSetRepository flashcardSetRepository,
            FlashcardStagingBatchRepository stagingBatchRepository,
            FlashcardStagingCardRepository stagingCardRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            @Qualifier("videoAiTaskExecutor") Executor executor,
            @Qualifier("videoAiLeaseScheduler") ScheduledExecutorService leaseScheduler,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.hlsProperties = hlsProperties;
        this.jobRepository = jobRepository;
        this.contentRepository = contentRepository;
        this.hlsLessonRepository = hlsLessonRepository;
        this.r2StorageClient = r2StorageClient;
        this.transcriptionService = transcriptionService;
        this.generationService = generationService;
        this.objectMapper = objectMapper;
        this.flashcardGenerationService = flashcardGenerationService;
        this.flashcardSetRepository = flashcardSetRepository;
        this.stagingBatchRepository = stagingBatchRepository;
        this.stagingCardRepository = stagingCardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.executor = executor;
        this.leaseScheduler = leaseScheduler;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.video-ai.worker-delay-ms:5000}")
    public void dispatch() {
        if (!properties.isEnabled()) return;
        if (!workerPermit.compareAndSet(false, true)) return;
        UUID leaseOwner = UUID.randomUUID();
        LeaseClaim claim;
        try {
            claim = transactions.execute(status -> claimOne(leaseOwner).orElse(null));
        } catch (RuntimeException exception) {
            workerPermit.set(false);
            log.warn("Video AI job claim failed: reason={}", exception.getMessage());
            return;
        }
        if (claim == null) {
            workerPermit.set(false);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    process(claim.jobId(), claim.leaseOwner());
                } finally {
                    workerPermit.set(false);
                }
            });
        } catch (RuntimeException exception) {
            try {
                transactions.executeWithoutResult(status -> releaseClaim(claim.jobId(), claim.leaseOwner()));
            } finally {
                workerPermit.set(false);
            }
        }
    }

    private Optional<LeaseClaim> claimOne(UUID leaseOwner) {
        Instant now = Instant.now();
        Optional<VideoAiJob> candidate = jobRepository.findClaimable(now, PageRequest.of(0, 1)).stream().findFirst();
        if (candidate.isEmpty()) return Optional.empty();
        VideoAiJob job = candidate.get();
        job.setStatus("processing");
        job.setStage(job.getContentId() == null ? "transcribing" : "generating");
        job.setProgressPercent(job.getContentId() == null ? 5 : 65);
        job.setAttemptCount(job.getAttemptCount() + 1);
        if (job.getStartedAt() == null) job.setStartedAt(now);
        Duration lease = normalizedLeaseDuration();
        job.setLeaseOwner(leaseOwner);
        job.setLeaseHeartbeatAt(now);
        job.setLeaseExpiresAt(now.plus(lease));
        VideoAiJob saved = jobRepository.save(job);
        return Optional.of(new LeaseClaim(saved.getId(), leaseOwner));
    }

    private void process(UUID jobId, UUID leaseOwner) {
        Path tempDir = null;
        LeaseGuard lease = startHeartbeat(jobId, leaseOwner);
        try {
            VideoAiJob snapshot = transactions.execute(status -> requireOwnedJob(jobId, leaseOwner));
            HlsLesson hls = requireCurrentHls(snapshot);
            if ("FLASHCARD_CANDIDATES".equals(snapshot.getJobType())) {
                processFlashcards(snapshot, lease);
                return;
            }
            if (snapshot.getContentId() == null) {
                tempDir = Files.createTempDirectory("smart-learnly-video-ai-");
                Path audio = tempDir.resolve("source.mp3");
                String bucket = requireAiAudioBucket();
                r2StorageClient.downloadObjectToFile(bucket, hls.getAiAudioObjectKey(), audio);
                lease.assertOwned();
                TranscriptionResult transcription = transcriptionService.transcribe(audio, snapshot.getSourceLanguage());
                lease.assertOwned();
                transactions.execute(status ->
                        saveTranscript(jobId, leaseOwner, transcription));
            }
            if (VIDEO_TRANSCRIPT.equals(snapshot.getJobType())) {
                transactions.executeWithoutResult(status -> completeTranscript(jobId, leaseOwner));
                return;
            }
            GenerationInput input = transactions.execute(status -> toGenerationInput(
                    requireOwnedContent(jobId, leaseOwner)));
            LearningAidResult aids = generationService.generate(input.language(), input.segments());
            lease.assertOwned();
            transactions.executeWithoutResult(status -> complete(jobId, leaseOwner, aids));
        } catch (Exception exception) {
            log.warn("Video AI job failed: jobId={} reason={}", jobId, exception.getMessage());
            if (!(exception instanceof LeaseLostException)) {
                transactions.executeWithoutResult(status -> failOrRetry(jobId, leaseOwner, exception));
            }
        } finally {
            lease.close();
            deleteRecursively(tempDir);
        }
    }

    private void processFlashcards(VideoAiJob job, LeaseGuard lease) {
        FlashcardInput input = transactions.execute(status -> {
            requireOwnedJob(job.getId(), lease.leaseOwner());
            VideoAiContent content = contentRepository.findById(job.getContentId()).orElseThrow();
            if (!job.getSourceVersion().equals(content.getSourceVersion())) throw new StaleVideoException();
            return new FlashcardInput(content.getTranscriptText(), content.getLanguage());
        });
        GenerationResult result = flashcardGenerationService.generate(new DocumentGenerationRequest(
                input.transcript(), List.of(), List.of(),
                job.getDesiredCount() == null ? 10 : job.getDesiredCount(),
                input.language(), job.getDifficulty(), "VIDEO_TRANSCRIPT", "AI video transcript"));
        lease.assertOwned();
        transactions.executeWithoutResult(status ->
                saveFlashcardCandidates(job.getId(), lease.leaseOwner(), result));
    }

    private void saveFlashcardCandidates(UUID jobId, UUID leaseOwner, GenerationResult result) {
        VideoAiJob job = requireOwnedJob(jobId, leaseOwner);
        requireCurrentHls(job);
        FlashcardSet set = flashcardSetRepository
                .findByCurriculumLessonIdAndDeletedAtIsNull(job.getTargetLessonId()).orElseThrow();
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setFlashcardSet(set);
        batch.setCurriculumLessonId(job.getTargetLessonId());
        batch.setCourse(courseRepository.findByIdAndDeletedAtIsNull(job.getCourseId()).orElseThrow());
        batch.setCreatedBy(userRepository.findByIdAndDeletedAtIsNull(job.getRequestedBy()).orElseThrow());
        batch.setSourceType("VIDEO_TRANSCRIPT");
        batch.setStatus("draft");
        batch.setSourceName("AI video transcript");
        batch.setSourceVideoAiContentId(job.getContentId());
        FlashcardStagingBatch savedBatch = stagingBatchRepository.save(batch);
        List<FlashcardStagingCard> cards = new java.util.ArrayList<>();
        List<GeneratedFlashcardCandidate> candidates = result == null || result.candidates() == null
                ? List.of() : result.candidates();
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            if (candidate == null || candidate.frontText() == null || candidate.frontText().isBlank()
                    || candidate.backText() == null || candidate.backText().isBlank()) continue;
            FlashcardStagingCard card = new FlashcardStagingCard();
            card.setBatch(savedBatch);
            card.setFrontText(candidate.frontText().trim());
            card.setBackText(candidate.backText().trim());
            card.setHint(candidate.hint());
            card.setExplanation(candidate.explanation());
            card.setSourceExcerpt(candidate.sourceExcerpt());
            card.setStatus("draft");
            card.setSortOrder(cards.size());
            cards.add(card);
        }
        if (cards.isEmpty()) throw new IllegalStateException("Flashcard generation returned no candidates");
        stagingCardRepository.saveAll(cards);
        job.setResultBatchId(savedBatch.getId());
        job.setResultSetId(set.getId());
        job.setStatus("completed");
        job.setStage("candidates_ready");
        job.setProgressPercent(100);
        job.setCompletedAt(Instant.now());
        clearLease(job);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    private HlsLesson requireCurrentHls(VideoAiJob job) {
        HlsLesson hls = hlsLessonRepository.findByLessonId(job.getLessonId()).orElseThrow();
        if (!hls.isReady() || !job.getSourceVersion().equals(hls.getProcessingJobId())
                || hls.getAiAudioObjectKey() == null || hls.getAiAudioObjectKey().isBlank()) {
            throw new StaleVideoException();
        }
        return hls;
    }

    private VideoAiContent saveTranscript(UUID jobId, UUID leaseOwner, TranscriptionResult result) {
        VideoAiJob job = requireOwnedJob(jobId, leaseOwner);
        requireCurrentHls(job);
        VideoAiContent content = new VideoAiContent();
        content.setLessonId(job.getLessonId());
        content.setLessonScope(job.getLessonScope());
        content.setCourseId(job.getCourseId());
        content.setClassId(job.getClassId());
        content.setSourceVersion(job.getSourceVersion());
        content.setLanguage(result.language());
        content.setTranscriptText(result.fullText());
        content.setCreatedBy(job.getRequestedBy());
        List<TranscriptionSegment> safeSegments = result.segments() == null ? List.of() : result.segments();
        for (int index = 0; index < safeSegments.size(); index++) {
            TranscriptionSegment source = safeSegments.get(index);
            VideoAiTranscriptSegment segment = new VideoAiTranscriptSegment();
            segment.setSegmentIndex(index);
            segment.setStartMs(source.startMs());
            segment.setEndMs(source.endMs());
            segment.setText(source.text());
            content.addSegment(segment);
        }
        if (content.getSegments().isEmpty()) throw new IllegalStateException("Transcription returned no segments");
        VideoAiContent saved = contentRepository.saveAndFlush(content);
        job.setContentId(saved.getId());
        if (VIDEO_TRANSCRIPT.equals(job.getJobType())) {
            job.setStage("transcript_ready");
            job.setProgressPercent(100);
        } else {
            job.setStage("generating");
            job.setProgressPercent(65);
        }
        jobRepository.save(job);
        return saved;
    }

    private void completeTranscript(UUID jobId, UUID leaseOwner) {
        VideoAiJob job = requireOwnedJob(jobId, leaseOwner);
        requireCurrentHls(job);
        requireOwnedContent(jobId, leaseOwner);
        job.setStatus("completed");
        job.setStage("transcript_ready");
        job.setProgressPercent(100);
        job.setCompletedAt(Instant.now());
        clearLease(job);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    private GenerationInput toGenerationInput(VideoAiContent content) {
        List<TranscriptionSegment> segments = content.getSegments().stream()
                .sorted(Comparator.comparing(VideoAiTranscriptSegment::getSegmentIndex))
                .map(value -> new TranscriptionSegment(value.getSegmentIndex(), value.getStartMs(),
                        value.getEndMs(), value.getText())).toList();
        return new GenerationInput(content.getId(), content.getLanguage(), segments);
    }

    private void complete(UUID jobId, UUID leaseOwner, LearningAidResult result) {
        VideoAiJob job = requireOwnedJob(jobId, leaseOwner);
        requireCurrentHls(job);
        VideoAiContent content = contentRepository.findById(job.getContentId()).orElseThrow();
        content.setSuggestedTitle(result.suggestedTitle());
        content.setSummary(result.summary());
        try {
            content.setKeyPointsJson(objectMapper.writeValueAsString(
                    result.keyPoints() == null ? List.of() : result.keyPoints()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize generated key points", exception);
        }
        List<VideoAiTranscriptSegment> segments = content.getSegments().stream()
                .sorted(Comparator.comparing(VideoAiTranscriptSegment::getSegmentIndex)).toList();
        List<VideoAiChapter> chapters = result.chapters().stream().map(source -> {
            int first = Math.max(0, Math.min(source.startSegmentIndex(), segments.size() - 1));
            int last = Math.max(first, Math.min(source.endSegmentIndex(), segments.size() - 1));
            VideoAiChapter chapter = new VideoAiChapter();
            chapter.setChapterIndex(0);
            chapter.setStartMs(segments.get(first).getStartMs());
            chapter.setEndMs(segments.get(last).getEndMs());
            chapter.setTitle(source.title());
            chapter.setSummary(source.summary());
            return chapter;
        }).toList();
        for (int index = 0; index < chapters.size(); index++) chapters.get(index).setChapterIndex(index);
        content.replaceChapters(chapters);
        content.setStatus("published");
        content.setPublishedBy(job.getRequestedBy());
        content.setPublishedAt(Instant.now());
        contentRepository.save(content);
        job.setStatus("completed");
        job.setStage("draft_ready");
        job.setProgressPercent(100);
        job.setCompletedAt(Instant.now());
        clearLease(job);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    private void failOrRetry(UUID jobId, UUID leaseOwner, Exception exception) {
        VideoAiJob job = jobRepository.findOwnedForUpdate(jobId, leaseOwner).orElse(null);
        if (job == null) return;
        if (exception instanceof StaleVideoException) {
            job.setStatus("superseded");
            if (job.getContentId() != null) contentRepository.findById(job.getContentId()).ifPresent(content -> {
                content.setStatus("superseded");
                contentRepository.save(content);
            });
        } else if (job.getAttemptCount() < Math.max(1, properties.getMaxAttempts())) {
            job.setStatus("pending");
            long delayMinutes = Math.min(30, 1L << Math.min(5, job.getAttemptCount() - 1));
            job.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(delayMinutes)));
            job.setStage("retry_wait");
        } else {
            job.setStatus("failed");
            job.setStage("failed");
            job.setCompletedAt(Instant.now());
        }
        clearLease(job);
        job.setErrorMessage(safeMessage(exception));
        jobRepository.save(job);
    }

    private void releaseClaim(UUID jobId, UUID leaseOwner) {
        jobRepository.findOwnedForUpdate(jobId, leaseOwner).ifPresent(job -> {
            job.setStatus("pending");
            job.setStage("queued");
            job.setAttemptCount(Math.max(0, job.getAttemptCount() - 1));
            clearLease(job);
            job.setNextAttemptAt(Instant.now().plusSeconds(5));
            jobRepository.save(job);
        });
    }

    private VideoAiJob requireOwnedJob(UUID jobId, UUID leaseOwner) {
        return jobRepository.findOwnedForUpdate(jobId, leaseOwner)
                .orElseThrow(LeaseLostException::new);
    }

    private VideoAiContent requireOwnedContent(UUID jobId, UUID leaseOwner) {
        VideoAiJob job = requireOwnedJob(jobId, leaseOwner);
        if (job.getContentId() == null) throw new IllegalStateException("Video AI content checkpoint is missing");
        return contentRepository.findById(job.getContentId()).orElseThrow();
    }

    private LeaseGuard startHeartbeat(UUID jobId, UUID leaseOwner) {
        AtomicBoolean lost = new AtomicBoolean(false);
        long intervalMillis = heartbeatIntervalMillis();
        ScheduledFuture<?> future = leaseScheduler.scheduleAtFixedRate(() -> {
            try {
                Boolean extended = transactions.execute(status -> heartbeat(jobId, leaseOwner));
                if (!Boolean.TRUE.equals(extended)) lost.set(true);
            } catch (RuntimeException exception) {
                log.warn("Video AI lease heartbeat failed temporarily: jobId={} reason={}",
                        jobId, exception.getMessage());
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return new LeaseGuard(leaseOwner, lost, future);
    }

    boolean heartbeat(UUID jobId, UUID leaseOwner) {
        Instant now = Instant.now();
        return jobRepository.extendLease(jobId, leaseOwner,
                now.plus(normalizedLeaseDuration()), now) == 1;
    }

    private Duration normalizedLeaseDuration() {
        Duration configured = properties.getLeaseDuration();
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return Duration.ofHours(3);
        }
        return configured.compareTo(Duration.ofSeconds(30)) < 0
                ? Duration.ofSeconds(30)
                : configured;
    }

    private long heartbeatIntervalMillis() {
        long oneThird = normalizedLeaseDuration().toMillis() / 3;
        return Math.max(5_000L, Math.min(60_000L, oneThird));
    }

    private void clearLease(VideoAiJob job) {
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setLeaseHeartbeatAt(null);
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof StaleVideoException) return "The source video changed before processing completed";
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Video AI processing failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String requireAiAudioBucket() {
        String bucket = hlsProperties.getAiAudioBucket();
        if (bucket != null && !bucket.isBlank()) return bucket;
        throw new IllegalStateException("Private AI audio bucket is not configured");
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.debug("Could not delete Video AI temporary file {}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.debug("Could not clean Video AI temporary directory {}", root, exception);
        }
    }

    private static final class StaleVideoException extends RuntimeException {
    }

    private static final class LeaseLostException extends RuntimeException {
    }

    private record LeaseClaim(UUID jobId, UUID leaseOwner) {
    }

    private record LeaseGuard(
            UUID leaseOwner,
            AtomicBoolean lost,
            ScheduledFuture<?> future
    ) implements AutoCloseable {
        void assertOwned() {
            if (lost.get()) throw new LeaseLostException();
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }

    private record GenerationInput(UUID contentId, String language, List<TranscriptionSegment> segments) {
    }

    private record FlashcardInput(String transcript, String language) {
    }
}
