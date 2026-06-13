DO $$
DECLARE
    current_udt_name text;
BEGIN
    SELECT column_info.udt_name
    INTO current_udt_name
    FROM information_schema.columns column_info
    WHERE column_info.table_schema = 'public'
      AND column_info.table_name = 'lessons'
      AND column_info.column_name = 'lesson_type';

    IF current_udt_name IS DISTINCT FROM 'lesson_type' THEN
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
    END IF;
END
$$;

ALTER TABLE public.lessons
    ALTER COLUMN lesson_type SET DEFAULT 'rich_text'::public.lesson_type;
