DO $$
BEGIN
    ALTER TYPE public.question_type ADD VALUE IF NOT EXISTS 'single_choice';
END $$;

ALTER TABLE public.ai_question_generation_drafts
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_drafts_type;

ALTER TABLE public.ai_question_generation_drafts
    ADD CONSTRAINT chk_ai_question_generation_drafts_type
    CHECK (question_type IN ('single_choice', 'multiple_choice', 'true_false'));
