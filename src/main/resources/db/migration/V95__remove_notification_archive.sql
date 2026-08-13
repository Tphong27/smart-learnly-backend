-- Các notification đã archive vốn không còn hiển thị; xóa chúng trước khi bỏ trạng thái archive.
DELETE FROM public.notifications
WHERE archived_at IS NOT NULL;

DROP INDEX IF EXISTS public.idx_notifications_user_active_created_at;
DROP INDEX IF EXISTS public.idx_notifications_user_active_unread_created_at;
DROP INDEX IF EXISTS public.idx_notifications_archived_created_at;
DROP INDEX IF EXISTS public.idx_notifications_retention_cleanup;

ALTER TABLE public.notifications
    DROP COLUMN IF EXISTS archived_at;

CREATE INDEX IF NOT EXISTS idx_notifications_retention_cleanup
    ON public.notifications (created_at)
    WHERE read_at IS NOT NULL;
