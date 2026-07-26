ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS rubric TEXT;
