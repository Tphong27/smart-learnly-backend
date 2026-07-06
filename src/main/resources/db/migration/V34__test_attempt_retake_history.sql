ALTER TABLE public.test_attempts
    ADD COLUMN IF NOT EXISTS retake_allowed boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_test_attempts_test_student_start
    ON public.test_attempts(test_id, student_id, start_time DESC);
