-- A dated class session used by the trainee schedule.
-- The shared database already contains a legacy class_sessions table,
-- therefore this migration must support both fresh and existing databases.

CREATE TABLE IF NOT EXISTS public.class_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    class_id uuid NOT NULL
        REFERENCES public.classes(id)
        ON DELETE CASCADE,

    session_date date NOT NULL,

    start_time time without time zone NOT NULL,

    end_time time without time zone NOT NULL,

    trainer_id uuid
        REFERENCES public.users(id),

    meeting_url text,

    created_at timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT chk_class_session_times
        CHECK (end_time > start_time)
);

-- Remove legacy columns which are no longer part of the selected model.
-- Supabase currently contains zero class_sessions, so no current session
-- data will be lost at this moment.
ALTER TABLE public.class_sessions
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS status;

-- Ensure the time constraint exists on the legacy table.
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
    ON public.class_sessions(
        class_id,
        session_date,
        start_time,
        end_time
    );

-- Supports fetching sessions of a class by week.
CREATE INDEX IF NOT EXISTS
    idx_class_sessions_class_date
    ON public.class_sessions(class_id, session_date);

ALTER TABLE public.class_sessions ENABLE ROW LEVEL SECURITY;

-- React must access schedules through the Spring Boot API.
REVOKE ALL ON public.class_sessions FROM anon, authenticated;