ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until timestamp with time zone,
    ADD COLUMN IF NOT EXISTS last_login_at timestamp with time zone;

ALTER TABLE public.refresh_tokens
    ADD COLUMN IF NOT EXISTS device_info character varying(255),
    ADD COLUMN IF NOT EXISTS ip_address character varying(45);

CREATE INDEX IF NOT EXISTS idx_users_google_id
    ON public.users (google_id)
    WHERE google_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_locked_until
    ON public.users (locked_until)
    WHERE locked_until IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at
    ON public.refresh_tokens (revoked_at)
    WHERE revoked_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.login_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
    email character varying NOT NULL,
    ip_address character varying(45),
    user_agent character varying(500),
    login_method character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_login_history_user_id
    ON public.login_history (user_id);

CREATE INDEX IF NOT EXISTS idx_login_history_created_at
    ON public.login_history (created_at);
