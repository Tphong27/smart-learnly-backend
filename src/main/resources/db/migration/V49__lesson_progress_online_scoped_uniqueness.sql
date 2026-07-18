-- Online:
-- (student_id, course_id, lesson_identity_id), class_id IS NULL
--
-- Offline:
-- (student_id, class_id, lesson_identity_id), class_id IS NOT NULL

ALTER TABLE public.lesson_progress
    DROP CONSTRAINT IF EXISTS lesson_progress_student_id_lesson_id_key,
    DROP CONSTRAINT IF EXISTS uk_lesson_progress_student_lesson;

DROP INDEX IF EXISTS public.uk_lesson_progress_student_lesson;

CREATE UNIQUE INDEX IF NOT EXISTS
    uq_lesson_progress_student_online_identity
ON public.lesson_progress (
    student_id,
    course_id,
    lesson_identity_id
)
WHERE class_id IS NULL
  AND lesson_identity_id IS NOT NULL;