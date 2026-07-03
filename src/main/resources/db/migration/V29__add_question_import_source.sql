ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS import_source character varying(50);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_questions_import_source'
          AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT chk_questions_import_source
            CHECK (
                import_source IS NULL
                OR import_source IN ('manual', 'excel_import', 'json_import', 'image_import')
            );
    END IF;
END
$$;

COMMENT ON COLUMN public.questions.import_source
    IS 'Question creation source. Null means legacy or unknown source.';
