DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'lesson_type'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.lesson_type AS ENUM ('rich_text', 'video', 'pdf', 'quiz', 'assignment');
    END IF;
END
$$;

ALTER TABLE public.lessons
    ALTER COLUMN lesson_type DROP DEFAULT;

ALTER TABLE public.lessons
    ALTER COLUMN lesson_type TYPE public.lesson_type
    USING CASE
        WHEN lesson_type IS NULL THEN 'rich_text'::public.lesson_type
        WHEN lower(lesson_type::text) IN ('rich_text', 'text', 'article', 'html', 'markdown') THEN 'rich_text'::public.lesson_type
        WHEN lower(lesson_type::text) = 'video' THEN 'video'::public.lesson_type
        WHEN lower(lesson_type::text) IN ('pdf', 'document') THEN 'pdf'::public.lesson_type
        WHEN lower(lesson_type::text) IN ('quiz', 'test') THEN 'quiz'::public.lesson_type
        WHEN lower(lesson_type::text) = 'assignment' THEN 'assignment'::public.lesson_type
        ELSE 'rich_text'::public.lesson_type
    END;

ALTER TABLE public.lessons
    ALTER COLUMN lesson_type SET DEFAULT 'rich_text'::public.lesson_type;
