ALTER TABLE public.ai_question_generation_batches
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_batches_count;

ALTER TABLE public.ai_question_generation_batches
    ADD CONSTRAINT chk_ai_question_generation_batches_count
    CHECK (requested_count BETWEEN 1 AND 20);
