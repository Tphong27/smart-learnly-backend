-- Phase-safe migration for course/module-scoped question management.
-- Legacy question_banks/course_sections are kept so older endpoints can be retired gradually.

ALTER TABLE public.questions
    ALTER COLUMN question_bank_id DROP NOT NULL;

ALTER TABLE public.ai_question_generation_batches
    ALTER COLUMN question_bank_id DROP NOT NULL;

DROP INDEX IF EXISTS public.uq_questions_bank_lower_text_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_course_lower_text_active
    ON public.questions (course_id, lower(question_text))
    WHERE status <> 'archived'::question_status;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'public'
          AND table_name = 'questions'
          AND constraint_name = 'questions_module_id_fkey'
    ) THEN
        ALTER TABLE public.questions
            DROP CONSTRAINT questions_module_id_fkey;
    END IF;
END $$;

UPDATE public.questions question
SET module_id = NULL
WHERE question.module_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.curriculum_sections module
      WHERE module.id = question.module_id
  );

ALTER TABLE public.questions
    ADD CONSTRAINT questions_module_id_fkey
    FOREIGN KEY (module_id)
    REFERENCES public.curriculum_sections(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_questions_course_module
    ON public.questions (course_id, module_id)
    WHERE module_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_batches_course_status
    ON public.ai_question_generation_batches (course_id, status, created_at DESC);
