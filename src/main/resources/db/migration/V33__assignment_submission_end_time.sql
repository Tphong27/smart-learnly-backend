ALTER TABLE public.assignment_submissions
    ADD COLUMN IF NOT EXISTS end_time timestamp with time zone;
