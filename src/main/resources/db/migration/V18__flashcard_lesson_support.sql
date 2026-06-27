ALTER TYPE public.lesson_type ADD VALUE IF NOT EXISTS 'assignment';
ALTER TYPE public.lesson_type ADD VALUE IF NOT EXISTS 'flashcard';

CREATE TABLE IF NOT EXISTS public.flashcard_sets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id uuid,
    course_id uuid,
    created_by uuid,
    title character varying(255) NOT NULL,
    description text,
    is_public boolean NOT NULL DEFAULT false,
    is_official boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.flashcards (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id uuid NOT NULL REFERENCES public.flashcard_sets(id) ON DELETE CASCADE,
    front_text text,
    back_text text,
    front_image_url character varying(500),
    back_image_url character varying(500),
    hint text,
    explanation text,
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.flashcard_progress (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    flashcard_id uuid NOT NULL REFERENCES public.flashcards(id) ON DELETE CASCADE,
    learning_status character varying(30),
    last_review_result character varying(30),
    repetitions integer NOT NULL DEFAULT 0,
    interval_days integer NOT NULL DEFAULT 0,
    ease_factor numeric NOT NULL DEFAULT 2.5,
    last_reviewed_at timestamp with time zone,
    next_review_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

ALTER TABLE public.flashcard_sets
    ADD COLUMN IF NOT EXISTS lesson_id uuid,
    ADD COLUMN IF NOT EXISTS course_id uuid,
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS title character varying(255),
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS is_public boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_official boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

UPDATE public.flashcard_sets
SET title = 'Untitled flashcard set'
WHERE title IS NULL;

ALTER TABLE public.flashcard_sets
    ALTER COLUMN title SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_sets_lesson_id_fkey'
          AND conrelid = 'public.flashcard_sets'::regclass
    ) THEN
        ALTER TABLE public.flashcard_sets
            ADD CONSTRAINT flashcard_sets_lesson_id_fkey
            FOREIGN KEY (lesson_id)
            REFERENCES public.lessons(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_sets_course_id_fkey'
          AND conrelid = 'public.flashcard_sets'::regclass
    ) THEN
        ALTER TABLE public.flashcard_sets
            ADD CONSTRAINT flashcard_sets_course_id_fkey
            FOREIGN KEY (course_id)
            REFERENCES public.courses(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_sets_created_by_fkey'
          AND conrelid = 'public.flashcard_sets'::regclass
    ) THEN
        ALTER TABLE public.flashcard_sets
            ADD CONSTRAINT flashcard_sets_created_by_fkey
            FOREIGN KEY (created_by)
            REFERENCES public.users(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_flashcard_sets_lesson_id
    ON public.flashcard_sets (lesson_id)
    WHERE lesson_id IS NOT NULL;

ALTER TABLE public.flashcards
    ADD COLUMN IF NOT EXISTS set_id uuid,
    ADD COLUMN IF NOT EXISTS front_text text,
    ADD COLUMN IF NOT EXISTS back_text text,
    ADD COLUMN IF NOT EXISTS front_image_url character varying(500),
    ADD COLUMN IF NOT EXISTS back_image_url character varying(500),
    ADD COLUMN IF NOT EXISTS hint text,
    ADD COLUMN IF NOT EXISTS explanation text,
    ADD COLUMN IF NOT EXISTS order_index integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

ALTER TABLE public.flashcards
    ALTER COLUMN front_text DROP NOT NULL,
    ALTER COLUMN back_text DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcards_set_id_fkey'
          AND conrelid = 'public.flashcards'::regclass
    ) THEN
        ALTER TABLE public.flashcards
            ADD CONSTRAINT flashcards_set_id_fkey
            FOREIGN KEY (set_id)
            REFERENCES public.flashcard_sets(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_flashcards_set_order
    ON public.flashcards (set_id, order_index)
    WHERE deleted_at IS NULL;

ALTER TABLE public.flashcard_progress
    ADD COLUMN IF NOT EXISTS student_id uuid,
    ADD COLUMN IF NOT EXISTS flashcard_id uuid,
    ADD COLUMN IF NOT EXISTS learning_status character varying(30),
    ADD COLUMN IF NOT EXISTS last_review_result character varying(30),
    ADD COLUMN IF NOT EXISTS repetitions integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS interval_days integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ease_factor numeric NOT NULL DEFAULT 2.5,
    ADD COLUMN IF NOT EXISTS last_reviewed_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS next_review_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_progress_student_id_fkey'
          AND conrelid = 'public.flashcard_progress'::regclass
    ) THEN
        ALTER TABLE public.flashcard_progress
            ADD CONSTRAINT flashcard_progress_student_id_fkey
            FOREIGN KEY (student_id)
            REFERENCES public.users(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_progress_flashcard_id_fkey'
          AND conrelid = 'public.flashcard_progress'::regclass
    ) THEN
        ALTER TABLE public.flashcard_progress
            ADD CONSTRAINT flashcard_progress_flashcard_id_fkey
            FOREIGN KEY (flashcard_id)
            REFERENCES public.flashcards(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_flashcard_progress_student_flashcard
    ON public.flashcard_progress (student_id, flashcard_id);

CREATE INDEX IF NOT EXISTS idx_flashcard_progress_student
    ON public.flashcard_progress (student_id);