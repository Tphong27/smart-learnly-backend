package com.smartlearnly.backend.videoai.service;

import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.video-ai", name = "enabled", havingValue = "true")
public class VideoAiReadyLessonReconciler {
    private final HlsLessonRepository hlsLessonRepository;
    private final VideoAiAutoPreparationService autoPreparationService;

    @EventListener(ApplicationReadyEvent.class)
    public void enqueueUnpreparedReadyVideos() {
        for (HlsLesson hls : hlsLessonRepository
                .findAllByHlsStatusAndAiAudioObjectKeyIsNotNull("ready")) {
            if (hls.getProcessingJobId() == null || hls.getAiAudioObjectKey().isBlank()) continue;
            try {
                autoPreparationService.enqueueAfterVideoReady(
                        hls.getLessonId(), hls.getProcessingJobId());
            } catch (RuntimeException exception) {
                log.warn("Could not reconcile AI preparation for lesson {}", hls.getLessonId(), exception);
            }
        }
    }
}
