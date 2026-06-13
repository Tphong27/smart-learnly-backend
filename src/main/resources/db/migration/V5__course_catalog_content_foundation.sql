DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'course_status'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.course_status AS ENUM ('draft', 'published', 'inactive');
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
        CREATE TYPE public.lesson_type AS ENUM ('video', 'pdf', 'rich_text');
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'lesson_status'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.lesson_status AS ENUM ('draft', 'published', 'inactive');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name character varying(150) NOT NULL,
    slug character varying(180) NOT NULL,
    description text,
    parent_id uuid REFERENCES public.categories(id) ON DELETE RESTRICT,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_categories_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT chk_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_slug_lower
    ON public.categories (lower(slug));

CREATE INDEX IF NOT EXISTS idx_categories_parent_sort
    ON public.categories (parent_id, sort_order, name);

CREATE INDEX IF NOT EXISTS idx_categories_active
    ON public.categories (is_active);

CREATE TABLE IF NOT EXISTS public.courses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title character varying(255) NOT NULL,
    slug character varying(280) NOT NULL,
    short_description text,
    description text,
    outcomes text,
    requirements text,
    language character varying(50),
    level character varying(30),
    category_id uuid NOT NULL REFERENCES public.categories(id) ON DELETE RESTRICT,
    creator_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
    thumbnail_url character varying(500),
    price numeric(12,2) NOT NULL DEFAULT 0,
    discounted_price numeric(12,2),
    is_free boolean NOT NULL DEFAULT false,
    status public.course_status NOT NULL DEFAULT 'draft',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone,
    CONSTRAINT chk_courses_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_courses_discounted_price CHECK (
        discounted_price IS NULL OR (discounted_price >= 0 AND discounted_price <= price)
    ),
    CONSTRAINT chk_courses_free_price CHECK (NOT is_free OR price = 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_courses_slug_lower_active
    ON public.courses (lower(slug))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_courses_category_status
    ON public.courses (category_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_courses_creator
    ON public.courses (creator_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_courses_created_at
    ON public.courses (created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS public.course_sections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    title character varying(255) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_course_sections_id_course UNIQUE (id, course_id),
    CONSTRAINT chk_course_sections_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_course_sections_course_sort
    ON public.course_sections (course_id, sort_order);

CREATE TABLE IF NOT EXISTS public.lessons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    section_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    lesson_type public.lesson_type NOT NULL,
    video_url character varying(500),
    content text,
    attachment_url character varying(500),
    duration_seconds integer,
    is_preview boolean NOT NULL DEFAULT false,
    status public.lesson_status NOT NULL DEFAULT 'draft',
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_lessons_section_course
        FOREIGN KEY (section_id, course_id)
        REFERENCES public.course_sections(id, course_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_lessons_duration_non_negative CHECK (
        duration_seconds IS NULL OR duration_seconds >= 0
    ),
    CONSTRAINT chk_lessons_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_lessons_section_sort
    ON public.lessons (section_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_lessons_course_status
    ON public.lessons (course_id, status);

COMMENT ON TABLE public.categories IS
    'Two-level course category hierarchy. Service validation prevents third-level categories.';

COMMENT ON TABLE public.courses IS
    'Course catalog and publication lifecycle foundation.';

COMMENT ON TABLE public.course_sections IS
    'Ordered course curriculum sections.';

COMMENT ON TABLE public.lessons IS
    'Ordered lessons with public-preview metadata and publication lifecycle.';
