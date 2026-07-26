package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoAiContentRepository extends JpaRepository<VideoAiContent, UUID> {
    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdIsNullOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope);

    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope, UUID classId);

    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdIsNullAndSourceVersionOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope, UUID sourceVersion);

    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdAndSourceVersionOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope, UUID classId, UUID sourceVersion);

    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdIsNullAndStatusAndSourceVersionOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope, String status, UUID sourceVersion);

    Optional<VideoAiContent> findFirstByLessonIdAndLessonScopeAndClassIdAndStatusAndSourceVersionOrderByUpdatedAtDesc(
            UUID lessonId, String lessonScope, UUID classId, String status, UUID sourceVersion);

    @Query("select content from VideoAiContent content where content.lessonId = :lessonId "
            + "and content.lessonScope = :scope and ((:classId is null and content.classId is null) or content.classId = :classId) "
            + "and content.status = 'published'")
    List<VideoAiContent> findPublishedForLesson(
            @Param("lessonId") UUID lessonId,
            @Param("scope") String scope,
            @Param("classId") UUID classId);

    @Query("select content from VideoAiContent content "
            + "where content.courseId = :courseId "
            + "and content.lessonScope = 'MASTER' "
            + "and content.classId is null "
            + "and content.status = 'published' "
            + "and content.transcriptText is not null "
            + "order by content.updatedAt desc")
    List<VideoAiContent> findPublishedMasterTranscriptsByCourseId(@Param("courseId") UUID courseId);
}
