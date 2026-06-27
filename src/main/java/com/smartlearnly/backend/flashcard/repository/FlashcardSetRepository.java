package com.smartlearnly.backend.flashcard.repository;

import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardSetRepository extends JpaRepository<FlashcardSet, UUID> {
    Optional<FlashcardSet> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.lesson.id = :lessonId
              and flashcardSet.deletedAt is null
            """)
    Optional<FlashcardSet> findByLessonIdAndDeletedAtIsNull(@Param("lessonId") UUID lessonId);

    @Query("""
            select count(flashcardSet) > 0
            from FlashcardSet flashcardSet
            where flashcardSet.lesson.id = :lessonId
              and flashcardSet.deletedAt is null
            """)
    boolean existsByLessonIdAndDeletedAtIsNull(@Param("lessonId") UUID lessonId);

    @Query(value = """
            SELECT
                course.id AS "courseId",
                course.title AS "courseTitle",
                course.slug AS "courseSlug",
                section.id AS "sectionId",
                section.title AS "sectionTitle",
                section.sort_order AS "sectionSortOrder",
                lesson.id AS "lessonId",
                lesson.title AS "lessonTitle",
                lesson.sort_order AS "lessonSortOrder",
                flashcard_set.id AS "setId",
                flashcard_set.title AS "setTitle",
                COUNT(card.id) AS "cardCount",
                COALESCE(SUM(CASE WHEN progress.learning_status = 'known' THEN 1 ELSE 0 END), 0) AS "knownCount",
                COALESCE(SUM(CASE WHEN progress.learning_status = 'learning' THEN 1 ELSE 0 END), 0) AS "stillLearningCount",
                COALESCE(SUM(CASE WHEN card.id IS NOT NULL AND progress.id IS NULL THEN 1 ELSE 0 END), 0) AS "notStartedCount",
                MAX(progress.last_reviewed_at) AS "lastReviewedAt"
            FROM public.flashcard_sets flashcard_set
            JOIN public.lessons lesson ON lesson.id = flashcard_set.lesson_id
            JOIN public.course_sections section ON section.id = lesson.section_id
            JOIN public.courses course ON course.id = lesson.course_id
            JOIN public.course_enrollments enrollment ON enrollment.course_id = course.id
            LEFT JOIN public.flashcards card
                ON card.set_id = flashcard_set.id
               AND card.deleted_at IS NULL
            LEFT JOIN public.flashcard_progress progress
                ON progress.flashcard_id = card.id
               AND progress.student_id = :studentId
            WHERE enrollment.student_id = :studentId
              AND enrollment.status IN ('active'::public.enroll_status, 'completed'::public.enroll_status)
              AND course.deleted_at IS NULL
              AND course.access_blocked_at IS NULL
              AND flashcard_set.deleted_at IS NULL
              AND CAST(lesson.lesson_type AS text) = 'flashcard'
              AND CAST(lesson.status AS text) <> 'inactive'
            GROUP BY
                course.id,
                course.title,
                course.slug,
                section.id,
                section.title,
                section.sort_order,
                lesson.id,
                lesson.title,
                lesson.sort_order,
                flashcard_set.id,
                flashcard_set.title
            ORDER BY
                course.title ASC,
                section.sort_order ASC,
                section.id ASC,
                lesson.sort_order ASC,
                lesson.id ASC,
                flashcard_set.title ASC
            """, nativeQuery = true)
    List<LearningFlashcardSetProjection> findLearningFlashcardsForStudent(@Param("studentId") UUID studentId);

    interface LearningFlashcardSetProjection {
        UUID getCourseId();

        String getCourseTitle();

        String getCourseSlug();

        UUID getSectionId();

        String getSectionTitle();

        Integer getSectionSortOrder();

        UUID getLessonId();

        String getLessonTitle();

        Integer getLessonSortOrder();

        UUID getSetId();

        String getSetTitle();

        Long getCardCount();

        Long getKnownCount();

        Long getStillLearningCount();

        Long getNotStartedCount();

        Instant getLastReviewedAt();
    }
}
