ALTER TABLE public.tests
    ADD COLUMN IF NOT EXISTS curriculum_section_id UUID;

CREATE INDEX IF NOT EXISTS idx_tests_curriculum_section_id
    ON public.tests (curriculum_section_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'tests_curriculum_section_id_fkey'
    ) THEN
        ALTER TABLE public.tests
            ADD CONSTRAINT tests_curriculum_section_id_fkey
            FOREIGN KEY (curriculum_section_id)
            REFERENCES public.curriculum_sections(id)
            ON DELETE SET NULL;
    END IF;
END $$;
