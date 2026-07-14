package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsUploadService {

    private final VideoProcessingService videoProcessingService;
    private final HlsLessonRepository hlsLessonRepository;
    private final LessonRepository lessonRepository;
    // Trainer edit CurriculumLesson (versioned) — cần lookup ở cả 2 bảng để HLS pipeline
    // hoạt động cho cả master Lesson lẫn class curriculum lesson.
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final HlsProperties hlsProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final CloudflareR2StorageClient r2StorageClient;
    private final HlsWorkflowDispatcher workflowDispatcher;
    private final HlsProcessingStateService processingStateService;

    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024; // 500MB

    /**
     * Request to upload and process a video for a lesson.
     */
    public record UploadRequest(
            UUID lessonId,
            MultipartFile videoFile,
            boolean replaceExisting
    ) {}

    /**
     * Response after initiating video processing.
     */
    public record UploadResponse(
            UUID lessonId,
            String status,
            String message,
            String hlsPath,
            UUID jobId
    ) {}

    /**
     * Response for processing status.
     */
    public record ProcessingStatus(
            UUID lessonId,
            String hlsStatus,
            String r2BasePath,
            String qualities,
            String message,
            Integer progressPercent,
            String currentStep,
            UUID jobId,
            String processingProvider
    ) {}

    /**
     * Event to update HLS progress (used for async progress updates)
     */
    public record HlsProgressEvent(UUID lessonId, int percent, String step) {}

    public record HlsProcessingRequestedEvent(
            UUID lessonId,
            Path stagedVideo,
            String originalFilename
    ) {}

    /**
     * Initiates video upload and HLS processing.
     * The processing runs asynchronously.
     */
    public UploadResponse initiateUpload(UploadRequest request) {
        UUID lessonId = request.lessonId();
        MultipartFile videoFile = request.videoFile();

        // Validate lesson exists trong 1 trong 2 bảng: legacy `lessons` (master authoring cũ) hoặc
        // `curriculum_lessons` (versioning mới — trainer/SME edit).
        if (lessonRepository.findById(lessonId).isEmpty()
                && curriculumLessonRepository.findById(lessonId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson not found: " + lessonId);
        }

        // Validate file
        validateVideoFile(videoFile);

        if (hlsProperties.usesGithubActions()) {
            return initiateGithubActionsUpload(request);
        }
        if (!hlsProperties.usesLocalProcessing()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Unsupported HLS processing provider"
            );
        }

        return initiateLocalUpload(request);
    }

    private UploadResponse initiateLocalUpload(UploadRequest request) {
        UUID lessonId = request.lessonId();
        MultipartFile videoFile = request.videoFile();

        // Check if already processing or ready
        var existingHls = hlsLessonRepository.findByLessonId(lessonId);
        if (existingHls.isPresent()) {
            String status = existingHls.get().getHlsStatus();
            if ("processing".equals(status)) {
                return new UploadResponse(
                        lessonId,
                        "processing",
                        "Video is already being processed",
                        null,
                        existingHls.get().getProcessingJobId()
                );
            }
            if (!request.replaceExisting() && !"pending".equals(status)) {
                return new UploadResponse(
                        lessonId,
                        status,
                        "HLS already exists. Set replaceExisting=true to replace.",
                        existingHls.get().getR2BasePath(),
                        existingHls.get().getProcessingJobId()
                );
            }
        }

        HlsLesson hlsLesson = existingHls.orElseGet(() -> {
            HlsLesson created = new HlsLesson();
            created.setLessonId(lessonId);
            return created;
        });
        hlsLesson.setHlsStatus("processing");
        hlsLesson.setR2BasePath(hlsProperties.getR2BasePath() + "/" + lessonId);
        hlsLesson.setQualities(hlsProperties.normalizedQualities());
        hlsLesson.setProgressPercent(0);
        hlsLesson.setCurrentStep("Initializing...");
        hlsLesson.setErrorMessage(null);
        hlsLessonRepository.save(hlsLesson);

        log.info("Initiating HLS processing for lesson {}", lessonId);

        Path stagedVideo = stageVideo(videoFile);
        eventPublisher.publishEvent(new HlsProcessingRequestedEvent(
                lessonId,
                stagedVideo,
                videoFile.getOriginalFilename()
        ));

        return new UploadResponse(
                lessonId,
                "processing",
                "Video uploaded successfully. Processing started.",
                hlsLesson.getR2BasePath(),
                null
        );
    }

    private UploadResponse initiateGithubActionsUpload(UploadRequest request) {
        validateGithubActionsConfiguration();
        String extension = extractVideoExtension(request.videoFile().getOriginalFilename());
        HlsProcessingStateService.ProcessingReservation reservation =
                processingStateService.reserveGithubJob(
                        request.lessonId(),
                        request.replaceExisting(),
                        extension
                );

        if (!reservation.started()) {
            return new UploadResponse(
                    request.lessonId(),
                    reservation.status(),
                    reservation.message(),
                    reservation.activePath(),
                    reservation.jobId()
            );
        }

        try {
            uploadRawVideo(request.videoFile(), reservation.sourceKey());
            workflowDispatcher.dispatch(new HlsWorkflowDispatcher.DispatchRequest(
                    reservation.jobId(),
                    request.lessonId(),
                    reservation.sourceKey(),
                    reservation.outputPrefix(),
                    hlsProperties.normalizedQualities(),
                    hlsProperties.normalizedSegmentDuration(),
                    hlsProperties.normalizedFfmpegPreset()
            ));
            processingStateService.markWorkflowDispatched(request.lessonId(), reservation.jobId());
        } catch (RuntimeException exception) {
            processingStateService.markJobFailed(
                    request.lessonId(),
                    reservation.jobId(),
                    exception.getMessage()
            );
            throw exception;
        }

        return new UploadResponse(
                request.lessonId(),
                "processing",
                "Video uploaded to private R2. GitHub Actions processing started.",
                reservation.outputPrefix(),
                reservation.jobId()
        );
    }

    /**
     * Async method to process video in background.
     */
    @Async("videoProcessingExecutor")
    @EventListener
    public void processVideoAsync(HlsProcessingRequestedEvent event) {
        UUID lessonId = event.lessonId();
        log.info("Starting async HLS processing for lesson {}", lessonId);

        try {
            VideoProcessingService.HlsProcessingResult result =
                    videoProcessingService.processVideoToHls(
                            event.stagedVideo(),
                            lessonId,
                            event.originalFilename(),
                            (percent, step) -> eventPublisher.publishEvent(
                                    new HlsProgressEvent(lessonId, percent, step)
                            )
                    );

            // Update HLS lesson with success status
            updateHlsLessonSuccessDirect(lessonId, result);

            // Update lesson with video URL
            updateLessonVideoUrlDirect(lessonId, result);

            log.info("HLS processing completed successfully for lesson {}", lessonId);

        } catch (Exception e) {
            log.error("HLS processing failed for lesson {}", lessonId, e);
            updateHlsLessonFailureDirect(lessonId, e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(event.stagedVideo());
            } catch (IOException cleanupError) {
                log.warn("Could not delete staged HLS upload {}", event.stagedVideo(), cleanupError);
            }
        }
    }

    /**
     * Event listener for HLS progress updates.
     * This runs in a separate transaction to update progress without blocking the main flow.
     */
    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleHlsProgressEvent(HlsProgressEvent event) {
        log.debug("Received HLS progress event: {}% - {}", event.percent, event.step);
        hlsLessonRepository.findByLessonId(event.lessonId).ifPresent(hlsLesson -> {
            hlsLesson.setProgressPercent(event.percent);
            hlsLesson.setCurrentStep(event.step);
            hlsLessonRepository.save(hlsLesson);
        });
    }

    // Direct database update methods (used after async processing completes)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateHlsLessonSuccessDirect(UUID lessonId, VideoProcessingService.HlsProcessingResult result) {
        hlsLessonRepository.findByLessonId(lessonId).ifPresent(hlsLesson -> {
            hlsLesson.setHlsStatus("ready");
            hlsLesson.setProgressPercent(100);
            hlsLesson.setCurrentStep("Complete");
            hlsLesson.setR2BasePath(result.r2BasePath());
            hlsLesson.setQualities(String.join(",", result.generatedQualities()));
            hlsLessonRepository.save(hlsLesson);
        });
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateHlsLessonFailureDirect(UUID lessonId, String errorMessage) {
        hlsLessonRepository.findByLessonId(lessonId).ifPresent(hlsLesson -> {
            hlsLesson.setHlsStatus("failed");
            hlsLesson.setErrorMessage(errorMessage);
            hlsLessonRepository.save(hlsLesson);
        });
    }

    @Transactional
    public void updateLessonVideoUrlDirect(UUID lessonId, VideoProcessingService.HlsProcessingResult result) {
        String videoUrl = result.masterPlaylistPath();
        // Ghi videoUrl vào đúng bảng chứa lesson. Chỉ 1 bảng có row → chỉ 1 ifPresent trúng.
        lessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoUrl(videoUrl);
            lessonRepository.save(lesson);
        });
        curriculumLessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoUrl(videoUrl);
            curriculumLessonRepository.save(lesson);
        });
    }

    /**
     * Gets the processing status of a lesson's HLS video.
     */
    @Transactional(readOnly = true)
    public ProcessingStatus getProcessingStatus(UUID lessonId) {
        return hlsLessonRepository.findByLessonId(lessonId)
                .map(hls -> new ProcessingStatus(
                        lessonId,
                        hls.getHlsStatus(),
                        hls.getR2BasePath(),
                        hls.getQualities(),
                        getStatusMessage(hls.getHlsStatus()),
                        hls.getProgressPercent() != null ? hls.getProgressPercent() : 0,
                        hls.getCurrentStep() != null ? hls.getCurrentStep() : getStatusMessage(hls.getHlsStatus()),
                        hls.getProcessingJobId(),
                        hls.getProcessingProvider()
                ))
                .orElse(new ProcessingStatus(
                        lessonId,
                        "not_found",
                        null,
                        null,
                        "No HLS video found for this lesson",
                        0,
                        "Not found",
                        null,
                        null
                ));
    }

    /**
     * Deletes HLS content for a lesson.
     */
    @Transactional
    public void deleteHlsVideo(UUID lessonId) {
        HlsLesson hlsLesson = hlsLessonRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS lesson not found"));

        hlsLessonRepository.delete(hlsLesson);

        // Clear video URL from lesson — thử cả 2 bảng.
        lessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoUrl(null);
            lessonRepository.save(lesson);
        });
        curriculumLessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoUrl(null);
            curriculumLessonRepository.save(lesson);
        });

        log.info("Deleted HLS content for lesson {}", lessonId);
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Video file is required");
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Video file too large. Maximum size is " + (MAX_VIDEO_SIZE / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Video file name is required");
        }

        boolean isVideo = contentType != null && contentType.startsWith("video/");
        boolean hasVideoExtension = fileName.toLowerCase(Locale.ROOT)
                .matches(".*\\.(mp4|mov|avi|mkv|webm|m4v|mpg|mpeg)$");

        if (!isVideo && !hasVideoExtension) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Invalid video file. Supported formats: mp4, mov, avi, mkv, webm, m4v, mpg, mpeg");
        }
    }

    private Path stageVideo(MultipartFile videoFile) {
        Path stagedVideo = null;
        try {
            stagedVideo = Files.createTempFile("smart-learnly-hls-", ".upload");
            videoFile.transferTo(stagedVideo);
            return stagedVideo;
        } catch (IOException | IllegalStateException exception) {
            if (stagedVideo != null) {
                try {
                    Files.deleteIfExists(stagedVideo);
                } catch (IOException cleanupError) {
                    exception.addSuppressed(cleanupError);
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not stage the uploaded video");
        }
    }

    public boolean isProcessingProviderAvailable() {
        if (hlsProperties.usesLocalProcessing()) {
            return videoProcessingService.isFfmpegAvailable();
        }
        if (hlsProperties.usesGithubActions()) {
            return githubBucketsConfigured() && workflowDispatcher.isConfigured();
        }
        return false;
    }

    private void validateGithubActionsConfiguration() {
        if (!githubBucketsConfigured() || !workflowDispatcher.isConfigured()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "GitHub Actions HLS processing is not configured"
            );
        }
    }

    private boolean githubBucketsConfigured() {
        return hlsProperties.getRawBucket() != null
                && !hlsProperties.getRawBucket().isBlank()
                && hlsProperties.getOutputBucket() != null
                && !hlsProperties.getOutputBucket().isBlank();
    }

    private void uploadRawVideo(MultipartFile videoFile, String sourceKey) {
        try (InputStream input = videoFile.getInputStream()) {
            r2StorageClient.putPrivateObject(
                    hlsProperties.getRawBucket(),
                    sourceKey,
                    videoFile.getContentType(),
                    input,
                    videoFile.getSize()
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not read the uploaded video");
        }
    }

    private String extractVideoExtension(String fileName) {
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Video file extension is required");
        }
        return normalized.substring(dot + 1);
    }

    private String getStatusMessage(String status) {
        return switch (status) {
            case "pending" -> "Waiting to be processed";
            case "processing" -> "Video is being processed into HLS format";
            case "ready" -> "Video is ready for streaming";
            case "failed" -> "Video processing failed. Please try again";
            default -> "Unknown status: " + status;
        };
    }
}
