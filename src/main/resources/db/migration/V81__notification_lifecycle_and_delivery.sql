ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS delivered_at timestamptz,
    ADD COLUMN IF NOT EXISTS seen_at timestamptz,
    ADD COLUMN IF NOT EXISTS clicked_at timestamptz,
    ADD COLUMN IF NOT EXISTS archived_at timestamptz;

UPDATE public.notifications
SET delivered_at = COALESCE(delivered_at, created_at, now())
WHERE delivered_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_active_created_at
    ON public.notifications (user_id, created_at DESC)
    WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_active_unread_created_at
    ON public.notifications (user_id, created_at DESC)
    WHERE archived_at IS NULL
      AND read_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_archived_created_at
    ON public.notifications (archived_at, created_at)
    WHERE archived_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_retention_cleanup
    ON public.notifications (created_at)
    WHERE archived_at IS NOT NULL
       OR read_at IS NOT NULL;
