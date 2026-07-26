package com.smartlearnly.backend.videoai.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoAiReadyLessonReconcilerTest {
    @Mock HlsLessonRepository hlsLessonRepository;
    @Mock VideoAiAutoPreparationService autoPreparationService;

    @Test
    void enqueuesExistingReadyVideosWhenTheApplicationStarts() {
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setHlsStatus("ready");
        hls.setProcessingJobId(sourceVersion);
        hls.setAiAudioObjectKey("ai/source.mp3");
        when(hlsLessonRepository.findAllByHlsStatusAndAiAudioObjectKeyIsNotNull("ready"))
                .thenReturn(List.of(hls));

        new VideoAiReadyLessonReconciler(hlsLessonRepository, autoPreparationService)
                .enqueueUnpreparedReadyVideos();

        verify(autoPreparationService).enqueueAfterVideoReady(lessonId, sourceVersion);
    }
}
