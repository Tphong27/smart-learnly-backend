-- FT-18 System Activity Log foundation.
-- Audit records are backend-only, append-only, and must never contain secrets.

CREATE TABLE public.audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamp with time zone NOT NULL DEFAULT now(),

    actor_type character varying(30) NOT NULL,
    actor_id uuid,
    actor_email character varying(255),
    actor_role character varying(30),

    action character varying(100) NOT NULL,
    domain character varying(50) NOT NULL,
    result character varying(20) NOT NULL,

    target_type character varying(50),
    target_id character varying(100),
    summary character varying(500) NOT NULL,

    old_values jsonb,
    new_values jsonb,
    metadata jsonb,

    ip_address character varying(45),
    user_agent character varying(500),
    correlation_id character varying(100),
    error_code character varying(100),

    CONSTRAINT audit_logs_actor_id_fkey
        FOREIGN KEY (actor_id)
        REFERENCES public.users(id)
        ON DELETE SET NULL,
    CONSTRAINT chk_audit_logs_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM', 'PAYMENT_PROVIDER')),
    CONSTRAINT chk_audit_logs_result
        CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT chk_audit_logs_action_not_blank
        CHECK (length(trim(action)) > 0),
    CONSTRAINT chk_audit_logs_domain_not_blank
        CHECK (length(trim(domain)) > 0),
    CONSTRAINT chk_audit_logs_summary_not_blank
        CHECK (length(trim(summary)) > 0)
);

CREATE INDEX idx_audit_logs_occurred_at
    ON public.audit_logs (occurred_at DESC, id DESC);

CREATE INDEX idx_audit_logs_domain_time
    ON public.audit_logs (domain, occurred_at DESC);

CREATE INDEX idx_audit_logs_action_time
    ON public.audit_logs (action, occurred_at DESC);

CREATE INDEX idx_audit_logs_actor_time
    ON public.audit_logs (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX idx_audit_logs_result_time
    ON public.audit_logs (result, occurred_at DESC);

CREATE INDEX idx_audit_logs_target
    ON public.audit_logs (target_type, target_id, occurred_at DESC)
    WHERE target_type IS NOT NULL AND target_id IS NOT NULL;

CREATE INDEX idx_audit_logs_correlation
    ON public.audit_logs (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.prevent_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON public.audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_audit_log_mutation();

ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.audit_logs FROM anon, authenticated;

COMMENT ON TABLE public.audit_logs IS
    'FT-18 append-only system activity audit ledger; backend access only.';
