package com.smartlearnly.backend.hls.service;
import java.util.UUID;
public interface HlsWorkflowDispatcher {
    void dispatch(DispatchRequest request);
    boolean isConfigured();
    record DispatchRequest(
            UUID jobId,
            UUID lessonId,
            String sourceKey,
            String outputPrefix,
            String qualities,
            int segmentDuration,
            String ffmpegPreset
    ) {}
}
