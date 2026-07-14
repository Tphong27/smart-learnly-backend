-- lesson_progress.lesson_id now stores the effective curriculum_lessons row ID.
-- It can no longer be constrained exclusively to the legacy lessons table.
-- Curriculum ownership and lesson validity are verified by
-- TraineeProgressService before progress is persisted.
ALTER TABLE public.lesson_progress
    DROP CONSTRAINT IF EXISTS lesson_progress_lesson_id_fkey;

COMMENT ON COLUMN public.lesson_progress.lesson_id IS
    'Effective curriculum lesson row ID used when the progress event was recorded.';
