package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.dto.HlsProcessingCallbackRequest;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.videoai.service.VideoAiAutoPreparationService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class HlsProcessingStateService {
    private final HlsLessonRepository hlsRepository;
    private final LessonRepository lessonRepository;
    // Hỗ trợ curriculum-versioned lesson: HLS pipeline có thể được kích hoạt từ trainer edit
    // trên `curriculum_lessons`, nên phải lookup + update ở cả 2 bảng.
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final HlsProperties properties;
    private final CloudflareR2StorageClient r2StorageClient;
    private final VideoAiAutoPreparationService videoAiAutoPreparationService;

    public record ProcessingReservation(boolean started, UUID jobId, String sourceKey,
            String outputPrefix, String status, String activePath, String message) {
    }

    @Transactional
    public ProcessingReservation reserveGithubJob(UUID lessonId, boolean replace, String extension) {
        lockLesson(lessonId);
        HlsLesson hls = hlsRepository.findByLessonId(lessonId).orElse(null);
        if (hls != null && "processing".equals(hls.getHlsStatus()))
            return existing(hls, "Video is already being processed");
        if (hls != null && !replace && !"pending".equals(hls.getHlsStatus()))
            return existing(hls, "HLS already exists. Set replaceExisting=true to replace.");
        UUID job = UUID.randomUUID();
        String base = properties.getR2BasePath();
        if (base == null || !base.matches("^[A-Za-z0-9][A-Za-z0-9_/-]*[A-Za-z0-9]$"))
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "HLS output prefix is invalid");
        String source = "raw/" + lessonId + "/" + job + "/source." + extension;
        String output = base.replaceAll("/+$", "") + "/" + lessonId + "/" + job;
        if (hls == null) {
            hls = new HlsLesson();
            hls.setLessonId(lessonId);
        }
        hls.setHlsStatus("processing");
        hls.setProcessingJobId(job);
        hls.setSourceObjectKey(source);
        hls.setProcessingOutputPrefix(output);
        hls.setProcessingProvider("github-actions");
        hls.setQualities(properties.normalizedQualities());
        hls.setProgressPercent(5);
        hls.setCurrentStep("Uploading source video to private R2");
        hls.setErrorMessage(null);
        hls.setWorkflowDispatchedAt(null);
        hls.setProcessingCompletedAt(null);
        hlsRepository.save(hls);
        return new ProcessingReservation(true, job, source, output, "processing", hls.getR2BasePath(), "Reserved");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWorkflowDispatched(UUID lessonId, UUID jobId) {
        lockLesson(lessonId);
        HlsLesson hls = current(lessonId, jobId);
        if (terminal(hls))
            return;
        hls.setWorkflowDispatchedAt(Instant.now());
        hls.setProgressPercent(20);
        hls.setCurrentStep("GitHub Actions is transcoding the video");
        hlsRepository.save(hls);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(UUID lessonId, UUID jobId, String error) {
        lockLesson(lessonId);
        HlsLesson hls = hlsRepository.findByLessonId(lessonId).orElse(null);
        if (hls == null || !jobId.equals(hls.getProcessingJobId()) || terminal(hls))
            return;
        fail(hls, error);
    }

    @Transactional
    public void applyCallback(HlsProcessingCallbackRequest request) {
        assertLessonExists(request.lessonId());
        HlsLesson hls = current(request.lessonId(), request.jobId());
        if (!request.isReady()) {
            if ("failed".equals(hls.getHlsStatus()))
                return;
            if ("ready".equals(hls.getHlsStatus()))
                throw new BusinessException(ErrorCode.CONFLICT, "Job completed");
            fail(hls, request.errorMessage());
            return;
        }
        String prefix = hls.getProcessingOutputPrefix();
        if (prefix == null || !prefix.equals(request.r2BasePath())
                || !(prefix + "/master.m3u8").equals(request.masterPlaylistPath())
                || request.qualities() == null || request.qualities().isEmpty())
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Callback output does not match reserved job");
        if ((request.aiAudioObjectKey() == null) != (request.aiAudioDurationMs() == null)
                || (request.aiAudioObjectKey() != null
                && !(prefix + "/ai/source.mp3").equals(request.aiAudioObjectKey()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Callback AI audio does not match reserved job");
        }
        if ("ready".equals(hls.getHlsStatus()) && prefix.equals(hls.getR2BasePath()))
            return;
        if (terminal(hls))
            throw new BusinessException(ErrorCode.CONFLICT, "Job already terminated");
        String previousAiAudioKey = hls.getAiAudioObjectKey();
        hls.setR2BasePath(prefix);
        hls.setEncryptionKeyPath(prefix + "/enc.key");
        hls.setQualities(String.join(",", request.qualities()));
        hls.setHlsStatus("ready");
        hls.setProgressPercent(100);
        hls.setCurrentStep("Complete");
        hls.setErrorMessage(null);
        hls.setProcessingCompletedAt(Instant.now());
        hls.setAiAudioObjectKey(request.aiAudioObjectKey());
        hls.setAiAudioDurationMs(request.aiAudioDurationMs());
        hlsRepository.save(hls);
        if (previousAiAudioKey != null && !previousAiAudioKey.equals(request.aiAudioObjectKey())) {
            afterCommit(() -> deleteAiAudioBestEffort(previousAiAudioKey));
        }
        String masterPlaylist = prefix + "/master.m3u8";
        // Ghi videoUrl vào bảng thực chứa lesson (legacy hoặc curriculum).
        lessonRepository.findByIdForUpdate(request.lessonId()).ifPresent(l -> {
            l.setVideoUrl(masterPlaylist);
            lessonRepository.save(l);
        });
        curriculumLessonRepository.findById(request.lessonId()).ifPresent(l -> {
            l.setVideoUrl(masterPlaylist);
            curriculumLessonRepository.save(l);
        });
        afterCommit(() -> videoAiAutoPreparationService.enqueueAfterVideoReady(
                request.lessonId(), request.jobId()));
    }

    private void fail(HlsLesson hls, String error) {
        String value = error == null || error.isBlank() ? "External HLS processing failed" : error.strip();
        hls.setHlsStatus("failed");
        hls.setCurrentStep("Processing failed");
        hls.setErrorMessage(value.substring(0, Math.min(1000, value.length())));
        hls.setProcessingCompletedAt(Instant.now());
        hlsRepository.save(hls);
    }

    private ProcessingReservation existing(HlsLesson h, String message) {
        return new ProcessingReservation(false, h.getProcessingJobId(), h.getSourceObjectKey(),
                h.getProcessingOutputPrefix(), h.getHlsStatus(), h.getR2BasePath(), message);
    }

    // Với 3 caller khác của lockLesson (reserveGithubJob / reserveLocalJob / reserveExistingJob),
    // ta chỉ cần lesson tồn tại — không cần entity. Đổi sang assertion tren cả 2 bảng.
    private void assertLessonExists(UUID id) {
        if (lessonRepository.findById(id).isEmpty()
                && curriculumLessonRepository.findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson not found: " + id);
        }
    }

    // Giữ tên cũ cho 3 callsite: reserveGithubJob / reserveLocalJob / reserveExistingJob.
    private void lockLesson(UUID id) {
        assertLessonExists(id);
    }

    private HlsLesson current(UUID lesson, UUID job) {
        HlsLesson h = hlsRepository.findByLessonId(lesson)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS job not found"));
        if (!job.equals(h.getProcessingJobId()))
            throw new BusinessException(ErrorCode.CONFLICT, "Stale HLS processing job");
        return h;
    }

    private boolean terminal(HlsLesson h) {
        return "ready".equals(h.getHlsStatus()) || "failed".equals(h.getHlsStatus());
    }

    private void deleteAiAudioBestEffort(String objectKey) {
        String bucket = properties.getAiAudioBucket();
        if (bucket == null || bucket.isBlank()) {
            return;
        }
        try {
            r2StorageClient.deleteObject(bucket, objectKey);
        } catch (RuntimeException ignored) {
            // The new video is already ready. Orphan cleanup can be retried operationally
            // and must not turn a successful signed callback into a failure.
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
