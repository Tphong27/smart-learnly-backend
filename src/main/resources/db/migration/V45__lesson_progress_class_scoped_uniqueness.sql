-- Lesson progress is class-scoped from V39 onward. The legacy uniqueness rule
-- on (student_id, lesson_id) prevents the same logical lesson from being
-- tracked independently when it is reused by more than one class.
ALTER TABLE public.lesson_progress
    DROP CONSTRAINT IF EXISTS uk_lesson_progress_student_lesson;

-- V22 may have created the legacy rule as a standalone index on databases
-- where the original table constraint did not exist.
DROP INDEX IF EXISTS public.uk_lesson_progress_student_lesson;

-- Keep the current class-aware invariant explicit for databases upgraded from
-- older migration histories.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lesson_progress_student_class_identity
    ON public.lesson_progress (student_id, class_id, lesson_identity_id)
    WHERE class_id IS NOT NULL
      AND lesson_identity_id IS NOT NULL;
