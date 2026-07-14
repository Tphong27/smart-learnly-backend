-- Per-answer media attachments for question answers (image / audio / video)
CREATE TABLE IF NOT EXISTS public.question_answer_media_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id uuid NOT NULL REFERENCES public.question_answers(id) ON DELETE CASCADE,
    media_type varchar(20) NOT NULL,
    media_url text NOT NULL,
    object_key text NOT NULL,
    bucket varchar(100) NOT NULL,
    original_file_name varchar(255),
    content_type varchar(100),
    file_size bigint NOT NULL DEFAULT 0,
    display_order integer NOT NULL DEFAULT 1,
    import_source varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_question_answer_media_type CHECK (media_type IN ('IMAGE', 'AUDIO', 'VIDEO')),
    CONSTRAINT chk_question_answer_media_display_order CHECK (display_order > 0),
    CONSTRAINT uq_question_answer_media_per_type UNIQUE (answer_id, media_type)
);

CREATE INDEX IF NOT EXISTS idx_question_answer_media_answer_type
    ON public.question_answer_media_attachments(answer_id, media_type);