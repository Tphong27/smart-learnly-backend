CREATE TABLE IF NOT EXISTS public.question_media_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES public.questions(id) ON DELETE CASCADE,
    media_type varchar(20) NOT NULL,
    media_url text NOT NULL,
    object_key text NOT NULL,
    bucket varchar(100) NOT NULL,
    original_file_name varchar(255),
    content_type varchar(100),
    file_size bigint NOT NULL DEFAULT 0,
    display_order integer NOT NULL,
    import_source varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_question_media_type CHECK (media_type IN ('IMAGE', 'AUDIO')),
    CONSTRAINT chk_question_media_display_order CHECK (display_order > 0),
    CONSTRAINT uq_question_media_order UNIQUE (question_id, media_type, display_order)
);

CREATE INDEX IF NOT EXISTS idx_question_media_question_type_order
    ON public.question_media_attachments(question_id, media_type, display_order);

INSERT INTO public.question_media_attachments (
    question_id,
    media_type,
    media_url,
    object_key,
    bucket,
    original_file_name,
    content_type,
    file_size,
    display_order,
    import_source,
    created_at,
    updated_at
)
SELECT
    q.id,
    'IMAGE',
    q.image_url,
    q.image_object_key,
    'question-images',
    NULLIF(regexp_replace(q.image_object_key, '^.*/', ''), ''),
    NULL,
    0,
    1,
    COALESCE(NULLIF(q.import_source, ''), 'manual'),
    q.created_at,
    q.updated_at
FROM public.questions q
WHERE q.image_url IS NOT NULL
  AND q.image_object_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.question_media_attachments qma
      WHERE qma.question_id = q.id
        AND qma.media_type = 'IMAGE'
        AND qma.display_order = 1
  );

INSERT INTO public.question_media_attachments (
    question_id,
    media_type,
    media_url,
    object_key,
    bucket,
    original_file_name,
    content_type,
    file_size,
    display_order,
    import_source,
    created_at,
    updated_at
)
SELECT
    q.id,
    'AUDIO',
    q.audio_url,
    q.audio_object_key,
    'question-audios',
    NULLIF(regexp_replace(q.audio_object_key, '^.*/', ''), ''),
    NULL,
    0,
    1,
    COALESCE(NULLIF(q.import_source, ''), 'manual'),
    q.created_at,
    q.updated_at
FROM public.questions q
WHERE q.audio_url IS NOT NULL
  AND q.audio_object_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.question_media_attachments qma
      WHERE qma.question_id = q.id
        AND qma.media_type = 'AUDIO'
        AND qma.display_order = 1
  );
