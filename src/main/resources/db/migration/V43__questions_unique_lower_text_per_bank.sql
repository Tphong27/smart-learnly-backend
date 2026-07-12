-- Prevent duplicate question text within the same question bank by lowercasing
-- the comparison. This guards against concurrent import-batch requests that pass
-- the application-level "exists" check at the same time.
--
-- Migration is idempotent: if duplicates exist we skip adding the constraint
-- and emit a NOTICE so the operator can clean the data first.

DO $$
DECLARE
    duplicate_count integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT question_bank_id, LOWER(question_text) AS normalized_text, COUNT(*) AS row_count
        FROM public.questions
        WHERE question_text IS NOT NULL
        GROUP BY question_bank_id, LOWER(question_text)
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE NOTICE 'questions_unique_lower_text_per_bank: skipped because % duplicate group(s) exist in public.questions. Resolve duplicates before re-running this migration.', duplicate_count;
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_questions_bank_lower_text'
          AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT uq_questions_bank_lower_text
            UNIQUE (question_bank_id, LOWER(question_text));
    END IF;
END
$$;

COMMENT ON CONSTRAINT uq_questions_bank_lower_text ON public.questions
    IS 'Ensures the same question text cannot appear twice within a single question bank (case-insensitive).';
