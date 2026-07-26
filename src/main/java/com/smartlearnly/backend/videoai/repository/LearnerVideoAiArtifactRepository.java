package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.LearnerVideoAiArtifact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnerVideoAiArtifactRepository extends JpaRepository<LearnerVideoAiArtifact, UUID> {
    Optional<LearnerVideoAiArtifact> findByStudentIdAndLessonIdAndSourceVersionAndArtifactType(
            UUID studentId,
            UUID lessonId,
            UUID sourceVersion,
            String artifactType
    );
}
