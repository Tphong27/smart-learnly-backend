DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public'
          AND type.typname = 'notification_type'
    ) THEN
        CREATE TYPE public.notification_type AS ENUM (
            'enrollment',
            'payment',
            'assignment',
            'test',
            'feedback',
            'system',
            'ai_suggestion',
            'class_reminder',
            'churn_alert',
            'class',
            'course'
        );
    END IF;
END
$$;

ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'enrollment';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'payment';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'assignment';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'test';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'feedback';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'system';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'ai_suggestion';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'class_reminder';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'churn_alert';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'class';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'course';

CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    type public.notification_type NOT NULL DEFAULT 'system',
    title varchar(255) NOT NULL,
    body text,
    reference_type varchar(80),
    reference_id uuid,
    action_url varchar(500),
    actor_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
    event_key varchar(200),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
DECLARE
    type_schema text;
    type_name text;
    type_data_type text;
    title_data_type text;
    reference_type_data_type text;
    reference_id_data_type text;
    action_url_data_type text;
    actor_id_data_type text;
    event_key_data_type text;
    payload_udt_name text;
    read_at_data_type text;
    created_at_data_type text;
    updated_at_data_type text;
    invalid_type text;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'id'
          AND data_type = 'uuid'
    ) THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: id must be uuid';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'user_id'
          AND data_type = 'uuid'
    ) THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: user_id must be uuid';
    END IF;

    ALTER TABLE public.notifications
        ADD COLUMN IF NOT EXISTS type public.notification_type,
        ADD COLUMN IF NOT EXISTS title varchar(255);

    SELECT columns.udt_schema, columns.udt_name, columns.data_type
    INTO type_schema, type_name, type_data_type
    FROM information_schema.columns columns
    WHERE columns.table_schema = 'public'
      AND columns.table_name = 'notifications'
      AND columns.column_name = 'type';

    IF type_schema = 'public' AND type_name = 'notification_type' THEN
        NULL;
    ELSIF type_data_type IN ('character varying', 'text') THEN
        SELECT notification.type::text
        INTO invalid_type
        FROM public.notifications notification
        WHERE notification.type IS NOT NULL
          AND btrim(notification.type::text) <> ''
          AND lower(replace(notification.type::text, '-', '_')) NOT IN (
              'enrollment',
              'payment',
              'assignment',
              'test',
              'feedback',
              'system',
              'ai_suggestion',
              'class_reminder',
              'churn_alert',
              'class',
              'course'
          )
        LIMIT 1;

        IF invalid_type IS NOT NULL THEN
            RAISE EXCEPTION 'Existing public.notifications table is incompatible: unsupported type value "%"', invalid_type;
        END IF;

        ALTER TABLE public.notifications
            ALTER COLUMN type DROP DEFAULT,
            ALTER COLUMN type TYPE public.notification_type
                USING COALESCE(NULLIF(lower(replace(type::text, '-', '_')), ''), 'system')::public.notification_type;
    ELSE
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: type must be public.notification_type or text/varchar';
    END IF;

    SELECT data_type INTO title_data_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'notifications'
      AND column_name = 'title';

    IF title_data_type NOT IN ('character varying', 'text') THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: title must be varchar or text';
    END IF;

    IF title_data_type = 'text' THEN
        ALTER TABLE public.notifications
            ALTER COLUMN title TYPE varchar(255)
                USING left(COALESCE(NULLIF(title::text, ''), 'Notification'), 255);
    END IF;

    ALTER TABLE public.notifications
        ADD COLUMN IF NOT EXISTS body text,
        ADD COLUMN IF NOT EXISTS reference_type varchar(80),
        ADD COLUMN IF NOT EXISTS reference_id uuid,
        ADD COLUMN IF NOT EXISTS action_url varchar(500),
        ADD COLUMN IF NOT EXISTS actor_id uuid,
        ADD COLUMN IF NOT EXISTS event_key varchar(200),
        ADD COLUMN IF NOT EXISTS payload jsonb DEFAULT '{}'::jsonb,
        ADD COLUMN IF NOT EXISTS read_at timestamptz,
        ADD COLUMN IF NOT EXISTS created_at timestamptz DEFAULT now(),
        ADD COLUMN IF NOT EXISTS updated_at timestamptz DEFAULT now();

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'message'
    ) THEN
        UPDATE public.notifications
        SET body = COALESCE(body, message::text),
            title = left(COALESCE(NULLIF(title, ''), NULLIF(message::text, ''), 'Notification'), 255);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'is_read'
          AND data_type = 'boolean'
    ) THEN
        UPDATE public.notifications
        SET read_at = COALESCE(read_at, updated_at, created_at, now())
        WHERE is_read = true
          AND read_at IS NULL;
    END IF;

    UPDATE public.notifications
    SET id = COALESCE(id, gen_random_uuid()),
        type = COALESCE(type, 'system'::public.notification_type),
        title = left(COALESCE(NULLIF(title, ''), 'Notification'), 255),
        payload = COALESCE(payload, '{}'::jsonb),
        created_at = COALESCE(created_at, now()),
        updated_at = COALESCE(updated_at, created_at, now());

    SELECT data_type INTO reference_type_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'reference_type';
    SELECT data_type INTO reference_id_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'reference_id';
    SELECT data_type INTO action_url_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'action_url';
    SELECT data_type INTO actor_id_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'actor_id';
    SELECT data_type INTO event_key_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'event_key';
    SELECT udt_name INTO payload_udt_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'payload';
    SELECT data_type INTO read_at_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'read_at';
    SELECT data_type INTO created_at_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'created_at';
    SELECT data_type INTO updated_at_data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'updated_at';

    IF reference_type_data_type NOT IN ('character varying', 'text') THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: reference_type must be varchar or text';
    ELSIF reference_type_data_type = 'text' THEN
        ALTER TABLE public.notifications ALTER COLUMN reference_type TYPE varchar(80) USING left(reference_type::text, 80);
    END IF;

    IF reference_id_data_type <> 'uuid' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: reference_id must be uuid';
    END IF;

    IF action_url_data_type NOT IN ('character varying', 'text') THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: action_url must be varchar or text';
    ELSIF action_url_data_type = 'text' THEN
        ALTER TABLE public.notifications ALTER COLUMN action_url TYPE varchar(500) USING left(action_url::text, 500);
    END IF;

    IF actor_id_data_type <> 'uuid' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: actor_id must be uuid';
    END IF;

    IF event_key_data_type NOT IN ('character varying', 'text') THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: event_key must be varchar or text';
    ELSIF event_key_data_type = 'text' THEN
        ALTER TABLE public.notifications ALTER COLUMN event_key TYPE varchar(200) USING left(event_key::text, 200);
    END IF;

    IF payload_udt_name <> 'jsonb' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: payload must be jsonb';
    END IF;

    IF read_at_data_type <> 'timestamp with time zone' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: read_at must be timestamptz';
    END IF;

    IF created_at_data_type <> 'timestamp with time zone' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: created_at must be timestamptz';
    END IF;

    IF updated_at_data_type <> 'timestamp with time zone' THEN
        RAISE EXCEPTION 'Existing public.notifications table is incompatible: updated_at must be timestamptz';
    END IF;

    ALTER TABLE public.notifications
        ALTER COLUMN id SET DEFAULT gen_random_uuid(),
        ALTER COLUMN user_id SET NOT NULL,
        ALTER COLUMN type SET DEFAULT 'system',
        ALTER COLUMN type SET NOT NULL,
        ALTER COLUMN title SET NOT NULL,
        ALTER COLUMN payload SET DEFAULT '{}'::jsonb,
        ALTER COLUMN payload SET NOT NULL,
        ALTER COLUMN created_at SET DEFAULT now(),
        ALTER COLUMN created_at SET NOT NULL,
        ALTER COLUMN updated_at SET DEFAULT now(),
        ALTER COLUMN updated_at SET NOT NULL;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notifications'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.notifications
            ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notifications'::regclass
          AND conname = 'fk_notifications_user'
    ) THEN
        ALTER TABLE public.notifications
            ADD CONSTRAINT fk_notifications_user
            FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notifications'::regclass
          AND conname = 'fk_notifications_actor'
    ) THEN
        ALTER TABLE public.notifications
            ADD CONSTRAINT fk_notifications_actor
            FOREIGN KEY (actor_id) REFERENCES public.users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notifications'::regclass
          AND conname = 'chk_notifications_title_not_blank'
    ) THEN
        ALTER TABLE public.notifications
            ADD CONSTRAINT chk_notifications_title_not_blank
            CHECK (btrim(title) <> '');
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notifications'::regclass
          AND conname = 'chk_notifications_event_key_not_blank'
    ) THEN
        ALTER TABLE public.notifications
            ADD CONSTRAINT chk_notifications_event_key_not_blank
            CHECK (event_key IS NULL OR btrim(event_key) <> '');
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at
    ON public.notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_created_at
    ON public.notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON public.notifications (reference_type, reference_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_event_key
    ON public.notifications (user_id, event_key)
    WHERE event_key IS NOT NULL;

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
