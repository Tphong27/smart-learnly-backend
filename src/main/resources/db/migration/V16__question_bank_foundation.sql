DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'question_type') THEN
        ALTER TYPE public.question_type ADD VALUE IF NOT EXISTS 'multiple_choice';
        ALTER TYPE public.question_type ADD VALUE IF NOT EXISTS 'true_false';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'question_status') THEN
        ALTER TYPE public.question_status ADD VALUE IF NOT EXISTS 'approved';
        ALTER TYPE public.question_status ADD VALUE IF NOT EXISTS 'archived';
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.question_banks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid REFERENCES public.courses(id),
    name character varying(255) NOT NULL,
    description text,
    created_by uuid REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

ALTER TABLE public.question_banks
    ADD COLUMN IF NOT EXISTS course_id uuid,
    ADD COLUMN IF NOT EXISTS name character varying(255),
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS status character varying(20) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

UPDATE public.question_banks
SET status = 'active'
WHERE status IS NULL;

UPDATE public.question_banks
SET updated_at = COALESCE(updated_at, created_at, now());

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'question_banks_course_id_fkey'
          AND conrelid = 'public.question_banks'::regclass
    ) THEN
        ALTER TABLE public.question_banks
            ADD CONSTRAINT question_banks_course_id_fkey
            FOREIGN KEY (course_id) REFERENCES public.courses(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'question_banks_created_by_fkey'
          AND conrelid = 'public.question_banks'::regclass
    ) THEN
        ALTER TABLE public.question_banks
            ADD CONSTRAINT question_banks_created_by_fkey
            FOREIGN KEY (created_by) REFERENCES public.users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_question_banks_status'
          AND conrelid = 'public.question_banks'::regclass
    ) THEN
        ALTER TABLE public.question_banks
            ADD CONSTRAINT chk_question_banks_status
            CHECK (status IN ('active', 'archived'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_question_bank_id_fkey'
          AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_question_bank_id_fkey
            FOREIGN KEY (question_bank_id) REFERENCES public.question_banks(id);
    END IF;
END
$$;

ALTER TABLE public.question_banks
    ALTER COLUMN course_id SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_question_banks_course_status
    ON public.question_banks(course_id, status);
CREATE INDEX IF NOT EXISTS idx_question_banks_updated_at
    ON public.question_banks(updated_at DESC);
