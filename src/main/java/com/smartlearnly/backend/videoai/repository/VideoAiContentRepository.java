package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoAiContentRepository extends JpaRepository<VideoAiContent, UUID> {
    @Query("select content from VideoAiContent content "
            + "where content.courseId = :courseId "
            + "and content.lessonScope = 'MASTER' "
            + "and content.classId is null "
            + "and content.status = 'published' "
            + "and content.transcriptText is not null "
            + "order by content.updatedAt desc")
    List<VideoAiContent> findPublishedMasterTranscriptsByCourseId(@Param("courseId") UUID courseId);
}
