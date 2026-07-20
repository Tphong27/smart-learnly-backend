package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoAiTranscriptSegmentRepository extends JpaRepository<VideoAiTranscriptSegment, UUID> {
}
