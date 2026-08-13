-- Ensure every materialized CLASS flashcard lesson has a direct set. Older
-- class drafts could clone the lesson row without cloning its flashcard set.
INSERT INTO public.flashcard_sets (
    curriculum_lesson_id,
    course_id,
    created_by,
    title,
    description,
    is_public,
    is_official
)
SELECT
    target_lesson.id,
    target_version.course_id,
    target_version.created_by,
    target_lesson.title,
    NULL,
    false,
    false
FROM public.curriculum_lessons target_lesson
JOIN public.curriculum_versions target_version
    ON target_version.id = target_lesson.curriculum_version_id
JOIN public.class_curriculum_entries target_entry
    ON target_entry.materialized_lesson_id = target_lesson.id
   AND target_entry.deleted_at IS NULL
WHERE target_version.scope = 'CLASS'
  AND target_lesson.lesson_type = 'flashcard'::public.lesson_type
  AND target_lesson.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.flashcard_sets target_set
      WHERE target_set.curriculum_lesson_id = target_lesson.id
        AND target_set.deleted_at IS NULL
  );

-- Restore cards only into sets that have never contained a card. This avoids
-- repopulating a set whose trainer intentionally removed every card by soft delete.
WITH repair_targets AS (
    SELECT
        target_set.id AS target_set_id,
        source_candidate.source_set_id
    FROM public.curriculum_lessons target_lesson
    JOIN public.curriculum_versions target_version
        ON target_version.id = target_lesson.curriculum_version_id
    JOIN public.class_curriculum_entries target_entry
        ON target_entry.materialized_lesson_id = target_lesson.id
       AND target_entry.deleted_at IS NULL
    JOIN public.flashcard_sets target_set
        ON target_set.curriculum_lesson_id = target_lesson.id
       AND target_set.deleted_at IS NULL
    CROSS JOIN LATERAL (
        SELECT source_set.id AS source_set_id
        FROM public.curriculum_lessons source_lesson
        JOIN public.curriculum_versions source_version
            ON source_version.id = source_lesson.curriculum_version_id
        JOIN public.flashcard_sets source_set
            ON source_set.curriculum_lesson_id = source_lesson.id
           AND source_set.deleted_at IS NULL
        WHERE source_lesson.lesson_identity_id = target_lesson.lesson_identity_id
          AND source_lesson.id <> target_lesson.id
          AND source_lesson.deleted_at IS NULL
          AND (
              source_version.scope = 'MASTER'
              OR source_version.class_id = target_version.class_id
          )
          AND EXISTS (
              SELECT 1
              FROM public.flashcards source_card
              WHERE source_card.set_id = source_set.id
                AND source_card.deleted_at IS NULL
          )
        ORDER BY
            CASE WHEN source_version.scope = 'CLASS' THEN 0 ELSE 1 END,
            source_version.version_number DESC,
            source_set.updated_at DESC,
            source_set.id DESC
        LIMIT 1
    ) source_candidate
    WHERE target_version.scope = 'CLASS'
      AND target_lesson.lesson_type = 'flashcard'::public.lesson_type
      AND target_lesson.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM public.flashcards existing_card
          WHERE existing_card.set_id = target_set.id
      )
)
INSERT INTO public.flashcards (
    set_id,
    front_text,
    back_text,
    order_index,
    front_image_url,
    back_image_url,
    hint,
    explanation
)
SELECT
    target.target_set_id,
    source_card.front_text,
    source_card.back_text,
    source_card.order_index,
    source_card.front_image_url,
    source_card.back_image_url,
    source_card.hint,
    source_card.explanation
FROM repair_targets target
JOIN public.flashcards source_card
    ON source_card.set_id = target.source_set_id
   AND source_card.deleted_at IS NULL;
