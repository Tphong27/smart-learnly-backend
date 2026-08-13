-- Repair class-only flashcard lessons left behind when the former two-request
-- frontend flow created the lesson but failed before creating its flashcard set.
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
    lesson.id,
    version.course_id,
    version.created_by,
    lesson.title,
    NULL,
    false,
    false
FROM public.curriculum_lessons lesson
JOIN public.curriculum_versions version
    ON version.id = lesson.curriculum_version_id
JOIN public.class_curriculum_entries entry
    ON entry.materialized_lesson_id = lesson.id
   AND entry.source_curriculum_lesson_id IS NULL
   AND entry.deleted_at IS NULL
WHERE lesson.lesson_type = 'flashcard'::public.lesson_type
  AND lesson.deleted_at IS NULL
  AND version.scope = 'CLASS'
  AND NOT EXISTS (
      SELECT 1
      FROM public.flashcard_sets flashcard_set
      WHERE flashcard_set.curriculum_lesson_id = lesson.id
        AND flashcard_set.deleted_at IS NULL
  );
