DO $$
DECLARE
    existing_values text[];
    desired_values constant text[] := ARRAY['draft', 'published', 'inactive'];
BEGIN
    SELECT COALESCE(array_agg(enum.enumlabel ORDER BY enum.enumsortorder), ARRAY[]::text[])
    INTO existing_values
    FROM pg_enum enum
    JOIN pg_type type ON type.oid = enum.enumtypid
    JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
    WHERE namespace.nspname = 'public'
      AND type.typname = 'course_status';

    IF existing_values = desired_values THEN
        RETURN;
    END IF;

    IF existing_values = ARRAY[]::text[] THEN
        RAISE EXCEPTION 'Cannot restrict course_status: public.course_status type does not exist';
    END IF;

    IF to_regclass('public.courses') IS NULL THEN
        RAISE EXCEPTION 'Cannot restrict course_status: public.courses table does not exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.courses
        WHERE status::text <> ALL (desired_values)
    ) THEN
        RAISE EXCEPTION 'Cannot restrict course_status: unsupported values still exist in public.courses.status';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public'
          AND type.typname = 'course_status_before_restrict'
    ) THEN
        RAISE EXCEPTION 'Cannot restrict course_status: temporary type public.course_status_before_restrict already exists';
    END IF;

    ALTER TABLE public.courses
        ALTER COLUMN status DROP DEFAULT;

    ALTER TYPE public.course_status RENAME TO course_status_before_restrict;

    CREATE TYPE public.course_status AS ENUM ('draft', 'published', 'inactive');

    ALTER TABLE public.courses
        ALTER COLUMN status TYPE public.course_status
        USING status::text::public.course_status;

    ALTER TABLE public.courses
        ALTER COLUMN status SET DEFAULT 'draft'::public.course_status;

    DROP TYPE public.course_status_before_restrict;
END $$;
