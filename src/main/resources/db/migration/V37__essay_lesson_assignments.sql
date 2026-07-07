ALTER TYPE public.lesson_type ADD VALUE IF NOT EXISTS 'essay';

ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS lesson_id uuid;

CREATE INDEX IF NOT EXISTS idx_assignments_lesson_id
    ON public.assignments(lesson_id)
    WHERE lesson_id IS NOT NULL;
