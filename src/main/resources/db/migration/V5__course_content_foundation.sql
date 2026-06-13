DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'course_status'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.course_status AS ENUM ('draft', 'published', 'archived');
    END IF;
END
$$;

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

CREATE TABLE IF NOT EXISTS public.categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name character varying(150) NOT NULL UNIQUE,
    slug character varying(180) NOT NULL UNIQUE,
    description text,
    parent_id uuid REFERENCES public.categories(id),
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.courses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id uuid NOT NULL REFERENCES public.categories(id),
    title character varying(200) NOT NULL,
    slug character varying(180) NOT NULL UNIQUE,
    description text,
    price numeric(12, 2) NOT NULL DEFAULT 0,
    status public.course_status NOT NULL DEFAULT 'draft',
    avatar_url character varying(500),
    creator_id uuid REFERENCES public.users(id),
    tags text[],
    is_featured boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone,
    CONSTRAINT chk_courses_price_non_negative CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS public.modules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    title character varying(200) NOT NULL,
    order_index integer NOT NULL DEFAULT 0,
    status character varying(20) NOT NULL DEFAULT 'active',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_modules_order_index_non_negative CHECK (order_index >= 0),
    CONSTRAINT chk_modules_status CHECK (status IN ('active', 'inactive'))
);

CREATE TABLE IF NOT EXISTS public.lessons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id uuid NOT NULL REFERENCES public.modules(id) ON DELETE CASCADE,
    title character varying(200) NOT NULL,
    content text,
    lesson_type public.lesson_type NOT NULL DEFAULT 'rich_text',
    order_index integer NOT NULL DEFAULT 0,
    is_preview boolean NOT NULL DEFAULT false,
    status character varying(20) NOT NULL DEFAULT 'active',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_lessons_order_index_non_negative CHECK (order_index >= 0),
    CONSTRAINT chk_lessons_status CHECK (status IN ('active', 'inactive'))
);

CREATE INDEX IF NOT EXISTS idx_courses_category_id
    ON public.courses (category_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_courses_status
    ON public.courses (status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_courses_creator_id
    ON public.courses (creator_id);

CREATE INDEX IF NOT EXISTS idx_modules_course_order
    ON public.modules (course_id, order_index);

CREATE INDEX IF NOT EXISTS idx_lessons_module_order
    ON public.lessons (module_id, order_index);

CREATE INDEX IF NOT EXISTS idx_lessons_preview
    ON public.lessons (is_preview, status)
    WHERE is_preview = true;
