-- Align question bank status with Sprint 4B MVP subset: draft, approved, archived.

ALTER TABLE public.question_banks
    DROP CONSTRAINT IF EXISTS chk_question_banks_status;

UPDATE public.question_banks
SET status = 'approved'
WHERE status = 'active';

UPDATE public.question_banks
SET status = 'draft'
WHERE status IS NULL OR status NOT IN ('draft', 'approved', 'archived');

ALTER TABLE public.question_banks
    ALTER COLUMN status SET DEFAULT 'draft';

ALTER TABLE public.question_banks
    ADD CONSTRAINT chk_question_banks_status
    CHECK (status IN ('draft', 'approved', 'archived'));
