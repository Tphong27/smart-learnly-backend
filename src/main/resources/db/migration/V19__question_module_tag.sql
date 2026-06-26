ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS module_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_module_id_fkey'
          AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_module_id_fkey
            FOREIGN KEY (module_id) REFERENCES public.course_sections(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_questions_module_id
    ON public.questions(module_id)
    WHERE module_id IS NOT NULL;