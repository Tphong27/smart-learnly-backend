-- Some pre-Flyway databases used notifications.content instead of body.
-- V78 added the canonical body column but did not relax the legacy NOT NULL
-- constraint, so inserts made by the Notification entity can still fail.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'content'
    ) THEN
        UPDATE public.notifications
        SET body = COALESCE(body, NULLIF(btrim(content::text), ''))
        WHERE body IS NULL;

        ALTER TABLE public.notifications
            ALTER COLUMN content DROP NOT NULL;
    END IF;
END
$$;
