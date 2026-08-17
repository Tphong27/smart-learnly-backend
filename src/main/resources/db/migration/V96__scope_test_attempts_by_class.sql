ALTER TABLE public.test_attempts
    ADD COLUMN IF NOT EXISTS class_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'test_attempts_class_id_fkey'
          AND conrelid = 'public.test_attempts'::regclass
    ) THEN
        ALTER TABLE public.test_attempts
            ADD CONSTRAINT test_attempts_class_id_fkey
            FOREIGN KEY (class_id)
            REFERENCES public.classes(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_test_attempts_test_student_class_start
    ON public.test_attempts (test_id, student_id, class_id, start_time DESC);
