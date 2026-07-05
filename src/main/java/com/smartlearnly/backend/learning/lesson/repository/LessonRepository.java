package com.smartlearnly.backend.learning.lesson.repository;

import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lesson from Lesson lesson where lesson.id = :lessonId")
    Optional<Lesson> findByIdForUpdate(@Param("lessonId") UUID lessonId);

    List<Lesson> findBySectionIdOrderBySortOrderAscCreatedAtAsc(UUID sectionId);

    Optional<Lesson> findByIdAndSectionId(UUID id, UUID sectionId);

    @Query("select coalesce(max(lesson.sortOrder), -1) from Lesson lesson where lesson.section.id = :sectionId")
    int findMaxSortOrderBySectionId(@Param("sectionId") UUID sectionId);

    @Query(value = """
            SELECT
                lesson.course_id AS "courseId",
                lesson.section_id AS "sectionId",
                lesson.id AS "lessonId",
                lesson.title AS "title",
                lesson.lesson_type::text AS "lessonType",
                lesson.video_url AS "videoUrl",
                lesson.content AS "content",
                lesson.attachment_url AS "attachmentUrl",
                lesson.duration_seconds AS "durationSeconds",
                section.sort_order AS "sectionSortOrder",
                lesson.sort_order AS "lessonSortOrder"
            FROM public.lessons lesson
            JOIN public.courses course ON course.id = lesson.course_id
            JOIN public.course_sections section ON section.id = lesson.section_id
            WHERE lesson.course_id = :courseId
              AND course.status = 'published'::public.course_status
              AND course.deleted_at IS NULL
              AND lesson.status = 'published'::public.lesson_status
              AND lesson.is_preview = true
            ORDER BY section.sort_order ASC, lesson.sort_order ASC, lesson.created_at ASC
            """, nativeQuery = true)
    List<PreviewLessonProjection> findPreviewLessons(@Param("courseId") UUID courseId);

    @Query(value = """
            SELECT
                lesson.course_id AS "courseId",
                lesson.section_id AS "sectionId",
                lesson.id AS "lessonId",
                lesson.title AS "title",
                lesson.lesson_type::text AS "lessonType",
                lesson.video_url AS "videoUrl",
                lesson.content AS "content",
                lesson.attachment_url AS "attachmentUrl",
                lesson.duration_seconds AS "durationSeconds",
                section.sort_order AS "sectionSortOrder",
                lesson.sort_order AS "lessonSortOrder"
            FROM public.lessons lesson
            JOIN public.courses course ON course.id = lesson.course_id
            JOIN public.course_sections section ON section.id = lesson.section_id
            WHERE lesson.course_id = :courseId
              AND lesson.id = :lessonId
              AND course.status = 'published'::public.course_status
              AND course.deleted_at IS NULL
              AND lesson.status = 'published'::public.lesson_status
              AND lesson.is_preview = true
            """, nativeQuery = true)
    Optional<PreviewLessonProjection> findPreviewLesson(
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId
    );
}
