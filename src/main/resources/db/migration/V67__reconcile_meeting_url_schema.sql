-- 1. Add the class-level Google Meet URL column.
ALTER TABLE public.classes
    ADD COLUMN IF NOT EXISTS meeting_url TEXT;

-- 2. Migrate existing non-empty URLs from class sessions to their classes.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'class_sessions'
          AND column_name = 'meeting_url'
    ) THEN
        EXECUTE $migration$
            UPDATE public.classes AS class_offering
            SET meeting_url = source.meeting_url
            FROM (
                SELECT DISTINCT ON (class_session.class_id)
                    class_session.class_id,
                    NULLIF(BTRIM(class_session.meeting_url), '') AS meeting_url
                FROM public.class_sessions AS class_session
                WHERE NULLIF(BTRIM(class_session.meeting_url), '') IS NOT NULL
                ORDER BY
                    class_session.class_id,
                    class_session.session_date,
                    class_session.start_time,
                    class_session.id
            ) AS source
            WHERE class_offering.id = source.class_id
              AND NULLIF(BTRIM(class_offering.meeting_url), '') IS NULL
        $migration$;
    END IF;
END $$;

-- 3. Document the new column.
COMMENT ON COLUMN public.classes.meeting_url IS
    'Google Meet URL shared by all sessions of the class';

-- 4. Remove the legacy session-level column after migrating its data.
ALTER TABLE public.class_sessions
    DROP COLUMN IF EXISTS meeting_url;