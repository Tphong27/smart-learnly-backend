ALTER TABLE public.lessons
    ADD COLUMN IF NOT EXISTS test_id uuid;

DO $$
BEGIN
    IF to_regclass('public.tests') IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'lessons_test_id_fkey'
              AND conrelid = 'public.lessons'::regclass
       ) THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT lessons_test_id_fkey
            FOREIGN KEY (test_id)
            REFERENCES public.tests(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_lessons_test_id
    ON public.lessons (test_id);
