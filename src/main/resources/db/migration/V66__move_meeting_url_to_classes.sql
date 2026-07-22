ALTER TABLE public.classes
    ADD COLUMN meeting_url VARCHAR(255);

ALTER TABLE public.classes
    ADD CONSTRAINT chk_classes_meeting_url_not_blank
    CHECK (
        meeting_url IS NULL
        OR BTRIM(meeting_url) <> ''
    );

-- Không được tự chọn một URL nếu một class cũ có nhiều URL khác nhau.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.class_sessions
        WHERE NULLIF(BTRIM(meeting_url), '') IS NOT NULL
        GROUP BY class_id
        HAVING COUNT(DISTINCT BTRIM(meeting_url)) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot migrate meeting URL: at least one class has multiple meeting URLs';
    END IF;
END $$;

-- Giữ lại URL cũ nếu môi trường khác đã từng nhập URL vào session.
UPDATE public.classes class_offering
SET meeting_url = session_link.meeting_url
FROM (
    SELECT
        class_id,
        MAX(BTRIM(meeting_url)) AS meeting_url
    FROM public.class_sessions
    WHERE NULLIF(BTRIM(meeting_url), '') IS NOT NULL
    GROUP BY class_id
) session_link
WHERE session_link.class_id = class_offering.id;

ALTER TABLE public.class_sessions
    DROP COLUMN meeting_url;