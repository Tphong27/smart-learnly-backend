-- Repair master curriculum flashcard lessons that were created through the generic
-- lesson endpoint before it enforced the lesson-to-set invariant.
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
WHERE lesson.lesson_type = 'flashcard'::public.lesson_type
  AND lesson.deleted_at IS NULL
  AND version.scope = 'MASTER'
  AND NOT EXISTS (
      SELECT 1
      FROM public.flashcard_sets flashcard_set
      WHERE flashcard_set.curriculum_lesson_id = lesson.id
        AND flashcard_set.deleted_at IS NULL
  );
