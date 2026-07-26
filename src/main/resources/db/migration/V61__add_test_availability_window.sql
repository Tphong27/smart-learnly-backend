ALTER TABLE public.tests
    ADD COLUMN IF NOT EXISTS opens_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closes_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_tests_availability_window'
          AND conrelid = 'public.tests'::regclass
    ) THEN
        ALTER TABLE public.tests
            ADD CONSTRAINT chk_tests_availability_window
            CHECK (
                opens_at IS NULL
                OR closes_at IS NULL
                OR opens_at < closes_at
            );
    END IF;
END
$$;
