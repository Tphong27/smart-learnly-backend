-- Reconcile the Sprint 2 course model with the pre-Flyway Supabase schema.
-- Existing tables and legacy columns are retained because other modules still
-- reference them.

ALTER TYPE public.course_status ADD VALUE IF NOT EXISTS 'inactive';

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

ALTER TABLE public.categories
    ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_categories_sort_order_non_negative'
          AND conrelid = 'public.categories'::regclass
    ) THEN
        ALTER TABLE public.categories
            ADD CONSTRAINT chk_categories_sort_order_non_negative CHECK (sort_order >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_categories_not_self_parent'
          AND conrelid = 'public.categories'::regclass
    ) THEN
        ALTER TABLE public.categories
            ADD CONSTRAINT chk_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_slug_lower
    ON public.categories (lower(slug));

CREATE INDEX IF NOT EXISTS idx_categories_parent_sort
    ON public.categories (parent_id, sort_order, name);

CREATE INDEX IF NOT EXISTS idx_categories_active
    ON public.categories (is_active);

ALTER TABLE public.courses
    ADD COLUMN IF NOT EXISTS short_description text,
    ADD COLUMN IF NOT EXISTS outcomes text,
    ADD COLUMN IF NOT EXISTS requirements text,
    ADD COLUMN IF NOT EXISTS language character varying(50),
    ADD COLUMN IF NOT EXISTS level character varying(30),
    ADD COLUMN IF NOT EXISTS thumbnail_url character varying(500),
    ADD COLUMN IF NOT EXISTS discounted_price numeric(12,2),
    ADD COLUMN IF NOT EXISTS is_free boolean NOT NULL DEFAULT false;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'courses'
          AND column_name = 'avatar_url'
    ) THEN
        UPDATE public.courses
        SET thumbnail_url = left(avatar_url, 500)
        WHERE thumbnail_url IS NULL
          AND avatar_url IS NOT NULL;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_courses_price_non_negative'
          AND conrelid = 'public.courses'::regclass
    ) THEN
        ALTER TABLE public.courses
            ADD CONSTRAINT chk_courses_price_non_negative CHECK (price >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_courses_discounted_price'
          AND conrelid = 'public.courses'::regclass
    ) THEN
        ALTER TABLE public.courses
            ADD CONSTRAINT chk_courses_discounted_price CHECK (
                discounted_price IS NULL OR (discounted_price >= 0 AND discounted_price <= price)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_courses_free_price'
          AND conrelid = 'public.courses'::regclass
    ) THEN
        ALTER TABLE public.courses
            ADD CONSTRAINT chk_courses_free_price CHECK (NOT is_free OR price = 0);
    END IF;
END
$$;

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

DO $$
BEGIN
    IF to_regclass('public.modules') IS NOT NULL THEN
        INSERT INTO public.course_sections (id, course_id, title, sort_order, created_at, updated_at)
        SELECT id, course_id, title, order_index, created_at, updated_at
        FROM public.modules
        ON CONFLICT (id) DO NOTHING;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_course_sections_course_sort
    ON public.course_sections (course_id, sort_order);

ALTER TABLE public.lessons
    ADD COLUMN IF NOT EXISTS course_id uuid,
    ADD COLUMN IF NOT EXISTS section_id uuid,
    ADD COLUMN IF NOT EXISTS video_url character varying(500),
    ADD COLUMN IF NOT EXISTS attachment_url character varying(500),
    ADD COLUMN IF NOT EXISTS duration_seconds integer,
    ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'lessons'
          AND column_name = 'module_id'
    ) THEN
        UPDATE public.lessons lesson
        SET course_id = section.course_id,
            section_id = section.id
        FROM public.course_sections section
        WHERE lesson.module_id = section.id
          AND (lesson.course_id IS NULL OR lesson.section_id IS NULL);

        ALTER TABLE public.lessons ALTER COLUMN module_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'lessons'
          AND column_name = 'order_index'
    ) THEN
        UPDATE public.lessons
        SET sort_order = order_index
        WHERE sort_order = 0
          AND order_index <> 0;
    END IF;

    IF EXISTS (SELECT 1 FROM public.lessons WHERE course_id IS NULL OR section_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot reconcile lessons with course sections: orphaned legacy lessons exist';
    END IF;
END
$$;

ALTER TABLE public.lessons
    ALTER COLUMN course_id SET NOT NULL,
    ALTER COLUMN section_id SET NOT NULL,
    ALTER COLUMN lesson_type SET DEFAULT 'rich_text'::public.lesson_type;

UPDATE public.lessons
SET lesson_type = 'rich_text'::public.lesson_type
WHERE lesson_type IS NULL;

ALTER TABLE public.lessons ALTER COLUMN lesson_type SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'lessons'
          AND column_name = 'status'
          AND udt_name <> 'lesson_status'
    ) THEN
        ALTER TABLE public.lessons DROP CONSTRAINT IF EXISTS lessons_status_check;
        ALTER TABLE public.lessons ALTER COLUMN status DROP DEFAULT;
        ALTER TABLE public.lessons
            ALTER COLUMN status TYPE public.lesson_status
            USING (
                CASE status
                    WHEN 'active' THEN 'published'
                    WHEN 'inactive' THEN 'inactive'
                    WHEN 'published' THEN 'published'
                    ELSE 'draft'
                END
            )::public.lesson_status;
        ALTER TABLE public.lessons
            ALTER COLUMN status SET DEFAULT 'draft'::public.lesson_status;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_course_sections_id_course'
          AND conrelid = 'public.course_sections'::regclass
    ) THEN
        ALTER TABLE public.course_sections
            ADD CONSTRAINT uq_course_sections_id_course UNIQUE (id, course_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_lessons_section_course'
          AND conrelid = 'public.lessons'::regclass
    ) THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT fk_lessons_section_course
            FOREIGN KEY (section_id, course_id)
            REFERENCES public.course_sections(id, course_id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_lessons_duration_non_negative'
          AND conrelid = 'public.lessons'::regclass
    ) THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT chk_lessons_duration_non_negative CHECK (
                duration_seconds IS NULL OR duration_seconds >= 0
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_lessons_sort_order_non_negative'
          AND conrelid = 'public.lessons'::regclass
    ) THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT chk_lessons_sort_order_non_negative CHECK (sort_order >= 0);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_lessons_section_sort
    ON public.lessons (section_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_lessons_course_status
    ON public.lessons (course_id, status);

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lessons ENABLE ROW LEVEL SECURITY;
