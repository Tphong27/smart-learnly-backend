-- A dated class session used by the trainee schedule.
-- Supports both a fresh database and the existing shared Supabase database.

CREATE TABLE IF NOT EXISTS public.class_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    class_id uuid NOT NULL
        REFERENCES public.classes(id)
        ON DELETE CASCADE,

    session_date date NOT NULL,

    start_time time without time zone NOT NULL,

    end_time time without time zone NOT NULL,

    trainer_id uuid NOT NULL
        REFERENCES public.users(id),

    meeting_url text,

    created_at timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT chk_class_session_times
        CHECK (end_time > start_time)
);

-- Remove columns from the legacy model.
ALTER TABLE public.class_sessions
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS status;

-- Do not silently convert sessions that do not have a trainer.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.class_sessions
        WHERE trainer_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot make class_sessions.trainer_id NOT NULL because sessions without a trainer exist';
    END IF;
END
$$;

ALTER TABLE public.class_sessions
    ALTER COLUMN trainer_id SET NOT NULL;

-- The existing Supabase database uses the old constraint name
-- chk_session_times for the same end_time > start_time condition.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_session_times'
          AND conrelid = 'public.class_sessions'::regclass
    )
    AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_class_session_times'
          AND conrelid = 'public.class_sessions'::regclass
    ) THEN
        ALTER TABLE public.class_sessions
            RENAME CONSTRAINT chk_session_times
            TO chk_class_session_times;
    END IF;
END
$$;

-- Ensure the time constraint exists if neither version was present.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_class_session_times'
          AND conrelid = 'public.class_sessions'::regclass
    ) THEN
        ALTER TABLE public.class_sessions
            ADD CONSTRAINT chk_class_session_times
            CHECK (end_time > start_time);
    END IF;
END
$$;

-- Prevent duplicate sessions in the same class.
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_class_sessions_class_date_time
    ON public.class_sessions (
        class_id,
        session_date,
        start_time,
        end_time
    );

-- The current Supabase database already has an equivalent index named
-- idx_class_sessions_class. Rename it instead of creating a duplicate.
DO $$
BEGIN
    IF to_regclass('public.idx_class_sessions_class_date') IS NULL
       AND to_regclass('public.idx_class_sessions_class') IS NOT NULL THEN
        ALTER INDEX public.idx_class_sessions_class
            RENAME TO idx_class_sessions_class_date;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS
    idx_class_sessions_class_date
    ON public.class_sessions (
        class_id,
        session_date
    );

-- Schedules must be accessed through the Spring Boot API.
ALTER TABLE public.class_sessions
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.class_sessions FROM anon, authenticated;