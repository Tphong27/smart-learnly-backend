CREATE TABLE IF NOT EXISTS public.lesson_resources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id uuid NOT NULL REFERENCES public.lessons(id) ON DELETE CASCADE,
    resource_url character varying(1000) NOT NULL,
    object_path character varying(1000),
    file_name character varying(255) NOT NULL,
    file_size bigint,
    content_type character varying(255),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_lesson_resources_file_size_non_negative CHECK (
        file_size IS NULL OR file_size >= 0
    ),
    CONSTRAINT chk_lesson_resources_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_lesson_resources_lesson_sort
    ON public.lesson_resources (lesson_id, sort_order);

ALTER TABLE public.lesson_resources ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.lesson_resources IS
    'Supplemental lesson resources uploaded by admins and linked to a lesson.';
