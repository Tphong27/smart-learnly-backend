DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'attempt_status') THEN
        ALTER TYPE public.attempt_status ADD VALUE IF NOT EXISTS 'doing';
        ALTER TYPE public.attempt_status ADD VALUE IF NOT EXISTS 'expired';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'submission_status') THEN
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'doing';
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'expired';
    END IF;
END
$$;

ALTER TABLE public.assignment_submissions
    ADD COLUMN IF NOT EXISTS start_time timestamp with time zone;

UPDATE public.assignment_submissions
SET start_time = COALESCE(start_time, created_at, submitted_at, now())
WHERE start_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_assignment_submissions_assignment_student
    ON public.assignment_submissions(assignment_id, student_id);
