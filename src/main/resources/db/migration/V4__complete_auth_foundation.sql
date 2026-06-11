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

CREATE TABLE IF NOT EXISTS public.otp_verifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    email character varying(255) NOT NULL,
    otp_hash character varying(255) NOT NULL,
    purpose character varying(30) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    verified_at timestamp with time zone,
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_otp_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT chk_otp_max_attempts_positive CHECK (max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_otp_verifications_email_purpose
    ON public.otp_verifications (lower(email), purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_otp_verifications_expires_at
    ON public.otp_verifications (expires_at);

CREATE TABLE IF NOT EXISTS public.user_security_limits (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.users(id) ON DELETE CASCADE,
    ip_address character varying(45),
    action_type character varying(30) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    last_attempt_at timestamp with time zone NOT NULL DEFAULT now(),
    locked_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_security_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_user_security_limits_lookup
    ON public.user_security_limits (user_id, ip_address, action_type);

CREATE INDEX IF NOT EXISTS idx_user_security_limits_locked_until
    ON public.user_security_limits (locked_until)
    WHERE locked_until IS NOT NULL;

COMMENT ON TABLE public.roles IS
    'Canonical role catalog. Existing users.role enum remains the active authorization field for Supabase compatibility.';
