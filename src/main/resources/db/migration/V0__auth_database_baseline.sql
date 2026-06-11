DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'user_role'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.user_role AS ENUM ('GUEST', 'TRAINEE', 'TRAINER', 'TMO', 'SME', 'ADMIN');
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE type.typname = 'user_status'
          AND namespace.nspname = 'public'
    ) THEN
        CREATE TYPE public.user_status AS ENUM ('pending_verify', 'active', 'inactive', 'banned');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.roles (
    id bigserial PRIMARY KEY,
    name character varying(50) NOT NULL UNIQUE,
    description character varying(255),
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

INSERT INTO public.roles (name, description)
VALUES
    ('GUEST', 'Unauthenticated visitor'),
    ('TRAINEE', 'Registered learner'),
    ('TRAINER', 'Class trainer'),
    ('TMO', 'Training Management Officer'),
    ('SME', 'Subject Matter Expert'),
    ('ADMIN', 'System administrator')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id uuid UNIQUE,
    email character varying(255) NOT NULL,
    password_hash character varying(255),
    google_id character varying(255) UNIQUE,
    full_name character varying(150) NOT NULL,
    avatar_url character varying(500),
    phone_number character varying(20),
    role public.user_role NOT NULL DEFAULT 'TRAINEE',
    status public.user_status NOT NULL DEFAULT 'pending_verify',
    bio text,
    ai_message_count integer NOT NULL DEFAULT 0,
    ai_quota_reset_date date,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON public.users (lower(email))
    WHERE deleted_at IS NULL;
