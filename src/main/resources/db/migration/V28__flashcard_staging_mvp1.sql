CREATE TABLE IF NOT EXISTS public.flashcard_staging_batches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    flashcard_set_id uuid NOT NULL REFERENCES public.flashcard_sets(id),
    lesson_id uuid NOT NULL REFERENCES public.lessons(id),
    course_id uuid NOT NULL REFERENCES public.courses(id),
    created_by uuid NOT NULL REFERENCES public.users(id),
    source_type character varying NOT NULL,
    status character varying NOT NULL DEFAULT 'draft',
    source_name character varying,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    approved_at timestamp with time zone,
    approved_by uuid REFERENCES public.users(id),
    CONSTRAINT chk_flashcard_staging_batches_source_type
        CHECK (source_type IN ('QUESTION_BANK', 'TEXT', 'DOCX', 'PDF', 'VIDEO_TRANSCRIPT', 'AI')),
    CONSTRAINT chk_flashcard_staging_batches_status
        CHECK (status IN ('draft', 'approved', 'cancelled'))
);

CREATE TABLE IF NOT EXISTS public.flashcard_staging_cards (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL REFERENCES public.flashcard_staging_batches(id),
    source_question_id uuid REFERENCES public.questions(id),
    front_text text,
    back_text text,
    front_image_url character varying,
    back_image_url character varying,
    hint text,
    explanation text,
    source_excerpt text,
    status character varying NOT NULL DEFAULT 'draft',
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_flashcard_staging_cards_status
        CHECK (status IN ('draft', 'approved', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_flashcard_staging_batches_flashcard_set_id
    ON public.flashcard_staging_batches(flashcard_set_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_batches_lesson_id
    ON public.flashcard_staging_batches(lesson_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_batches_course_id
    ON public.flashcard_staging_batches(course_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_batches_status
    ON public.flashcard_staging_batches(status);

CREATE INDEX IF NOT EXISTS idx_flashcard_staging_cards_batch_id
    ON public.flashcard_staging_cards(batch_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_cards_source_question_id
    ON public.flashcard_staging_cards(source_question_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_cards_status
    ON public.flashcard_staging_cards(status);
CREATE INDEX IF NOT EXISTS idx_flashcard_staging_cards_batch_sort_order
    ON public.flashcard_staging_cards(batch_id, sort_order);
